#!/usr/bin/env bash
# ============================================================
# 心飞交友 - 增量构建范围判定（CI 使用）
#
# 目标：改一个模块，只重新编译/打包/重建受影响的微服务，
#       其余服务保留旧容器继续运行，不做无意义的全量重构。
#
# 判定方式：源码指纹（不依赖 git 历史深度，浅克隆也能工作）
#   - 每个模块计算一个指纹 = 该模块全部受版本控制的源码内容的 SHA256 汇总
#   - 与上一次构建记录在 $CI_STATE_DIR/fingerprints/ 下的指纹比对
#   - 差异即为「变更模块」
#   - 再沿内部依赖图做反向闭包：模块 A 变更 → 依赖 A 的模块也必须重打
#     （本项目 xfjy-api 被 9 个服务依赖，改 api 会牵连全部服务，这是正确行为）
#
# 输入环境变量：
#   CI_STATE_DIR  持久化状态目录（宿主挂载），默认 /opt/ci-state
#   FORCE_FULL    =true 时忽略增量判定，强制全量构建
#
# 输出（写入 $CI_STATE_DIR/affected.env，供 workflow 后续步骤 source）：
#   BACKEND_AFFECTED   受影响的后端服务（可部署的 11 个之一）
#   FRONTEND_AFFECTED  受影响的前端（xfjy-admin / xfjy-h5）
#   BUILD_MODULES      需要执行 mvn package 的模块
#   FULL_BUILD         =true 表示本次为全量构建
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CI_STATE_DIR="${CI_STATE_DIR:-/opt/ci-state}"
FORCE_FULL="${FORCE_FULL:-false}"
FINGERPRINT_DIR="${CI_STATE_DIR}/fingerprints"
JAR_CACHE_DIR="${CI_STATE_DIR}/jars"
AFFECTED_ENV="${CI_STATE_DIR}/affected.env"

# ---- 服务清单 ----
# 可部署的后端服务（xfjy-api 只是接口库，不单独部署）
BACKEND_SERVICES=(
  xfjy-gateway xfjy-sso xfjy-user-service xfjy-circle-service
  xfjy-video-service xfjy-match-service xfjy-message-service
  xfjy-server xfjy-recommend xfjy-elasticsearch xfjy-pay-service
)
# 全部 Maven 模块（含接口库 xfjy-api）
ALL_MODULES=(
  xfjy-gateway xfjy-sso xfjy-user-service xfjy-circle-service
  xfjy-video-service xfjy-match-service xfjy-message-service
  xfjy-api xfjy-server xfjy-recommend xfjy-elasticsearch xfjy-pay-service
)

RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
log()  { printf '%s[inc-build]%s %s\n' "${CYAN}" "${RESET}" "$*"; }
ok()   { printf '%s[ OK ]%s %s\n' "${GREEN}" "${RESET}" "$*"; }
warn() { printf '%s[WARN]%s %s\n' "${YELLOW}" "${RESET}" "$*"; }
err()  { printf '%s[FAIL]%s %s\n' "${RED}" "${RESET}" "$*" >&2; }

mkdir -p "${CI_STATE_DIR}" "${FINGERPRINT_DIR}" "${JAR_CACHE_DIR}"

# ---- 工具函数 ----
is_backend_service() {
  local m
  for m in "${BACKEND_SERVICES[@]}"; do
    [ "$m" = "$1" ] && return 0
  done
  return 1
}

# 计算目录内容指纹：目录下所有文件（排除忽略项）内容的 SHA256 汇总
# 用法: dir_fingerprint <目录> [额外要纳入的文件...]
# 性能：一次性把文件列表喂给 sha256sum（避免每个文件起一个子进程）
dir_fingerprint() {
  local dir="$1"; shift
  local file_list
  file_list="$(mktemp)"
  {
    for extra in "$@"; do
      [ -f "${extra}" ] && printf '%s\n' "${extra}"
    done
    if [ -d "${dir}" ]; then
      find "${dir}" \
        -type d \( -name node_modules -o -name dist -o -name target -o -name .git \
                  -o -name .idea -o -name .workbuddy -o -name unpackage -o -name .hbuilderx \) -prune -o \
        -type f -print 2>/dev/null
    fi
  } | LC_ALL=C sort -u > "${file_list}"
  if [ -s "${file_list}" ]; then
    xargs -d '\n' sha256sum < "${file_list}" 2>/dev/null
  fi
  rm -f "${file_list}"
}

# 模块内部依赖：读取 pom 中对其它 xfjy-* 模块的依赖
module_deps() {
  local mod="$1" dep
  [ -f "${mod}/pom.xml" ] || return 0
  grep -o '<artifactId>xfjy-[a-z0-9-]*</artifactId>' "${mod}/pom.xml" 2>/dev/null \
    | sed 's|<artifactId>||; s|</artifactId>||' \
    | grep -v "^${mod}$" \
    | sort -u
}

# 反向依赖图：被依赖模块 -> 依赖它的模块列表
declare -A REVERSE_DEPS=()
build_reverse_graph() {
  local mod dep
  for mod in "${ALL_MODULES[@]}"; do
    while read -r dep; do
      [ -z "${dep}" ] && continue
      REVERSE_DEPS["${dep}"]="${REVERSE_DEPS[${dep}]:-} ${mod}"
    done < <(module_deps "${mod}")
  done
}

# 反向闭包：某模块变更后必须一起重打的服务
DOWNSTREAM=()
add_downstream() {
  local seed="$1"
  local queue=("${seed}")
  local cur dep
  while [ ${#queue[@]} -gt 0 ]; do
    cur="${queue[0]}"
    queue=("${queue[@]:1}")
    for dep in ${REVERSE_DEPS[${cur}]:-}; do
      case " ${DOWNSTREAM[*]:-} " in
        *" ${dep} "*) continue ;;
      esac
      DOWNSTREAM+=("${dep}")
      queue+=("${dep}")
    done
  done
}

# 去重输出
uniq_list() {
  local -a out=()
  local x y seen
  for x in "$@"; do
    [ -z "${x}" ] && continue
    seen=false
    for y in "${out[@]:-}"; do
      [ "$x" = "$y" ] && { seen=true; break; }
    done
    [ "${seen}" = false ] && out+=("$x")
  done
  [ ${#out[@]} -gt 0 ] && printf '%s\n' "${out[@]}"
}

# ---- 1. 计算当前指纹 ----
log "计算源码指纹..."
declare -A CUR_FP=()
for m in "${ALL_MODULES[@]}"; do
  CUR_FP["${m}"]="$(dir_fingerprint "${m}/src" "${m}/pom.xml" | sha256sum | cut -d' ' -f1)"
done
CUR_FP["__root_pom__"]="$(sha256sum pom.xml | cut -d' ' -f1)"
CUR_FP["__admin__"]="$(dir_fingerprint xfjy-admin/src xfjy-admin/package.json xfjy-admin/vite.config.js xfjy-admin/index.html | sha256sum | cut -d' ' -f1)"
CUR_FP["__app__"]="$(dir_fingerprint xfjy-app/src xfjy-app/package.json xfjy-app/vite.config.js xfjy-app/manifest.json | sha256sum | cut -d' ' -f1)"
CUR_FP["__deploy_web__"]="$(dir_fingerprint deploy/docker deploy/docker-compose.web.yml | sha256sum | cut -d' ' -f1)"
CUR_FP["__deploy_app__"]="$(dir_fingerprint deploy/docker deploy/docker-compose.app.yml | sha256sum | cut -d' ' -f1)"

# ---- 2. 与上次比对 ----
FULL_BUILD=false
CHANGED=()

if [ "${FORCE_FULL}" = "true" ]; then
  warn "FORCE_FULL=true，强制全量构建"
  FULL_BUILD=true
elif [ ! -f "${FINGERPRINT_DIR}/__root_pom__.fp" ]; then
  warn "首次构建（无指纹基线），按全量构建"
  FULL_BUILD=true
else
  for key in "${!CUR_FP[@]}"; do
    old="$(cat "${FINGERPRINT_DIR}/${key}.fp" 2>/dev/null || echo '')"
    if [ "${old}" != "${CUR_FP[${key}]}" ]; then
      CHANGED+=("${key}")
    fi
  done
fi

# ---- 3. 计算受影响模块（含依赖反向闭包） ----
AFFECTED_MODULES=()
DOWNSTREAM=()

if [ "${FULL_BUILD}" = "true" ]; then
  AFFECTED_MODULES=("${ALL_MODULES[@]}")
else
  build_reverse_graph
  for key in "${CHANGED[@]}"; do
    case "${key}" in
      __root_pom__)
        warn "根 pom.xml 变更 → 全量重建"
        FULL_BUILD=true
        AFFECTED_MODULES=("${ALL_MODULES[@]}")
        break
        ;;
      __deploy_app__|__deploy_web__)
        # 编排/镜像文件变更：容器层要重建，但代码 jar 不必重打
        ;;
      __admin__|__app__)
        ;;
      *)
        case " ${AFFECTED_MODULES[*]:-} " in
          *" ${key} "*) continue ;;
        esac
        AFFECTED_MODULES+=("${key}")
        add_downstream "${key}"
        ;;
    esac
  done
  for m in ${DOWNSTREAM[*]:-}; do
    case " ${AFFECTED_MODULES[*]:-} " in
      *" ${m} "*) continue ;;
    esac
    AFFECTED_MODULES+=("${m}")
  done
fi

# ---- 4. 拆分后端 / 前端 ----
BACKEND_AFFECTED=()
FRONTEND_AFFECTED=()

if [ "${FULL_BUILD}" = "true" ]; then
  BACKEND_AFFECTED=("${BACKEND_SERVICES[@]}")
  FRONTEND_AFFECTED=("xfjy-admin" "xfjy-h5")
else
  for m in "${AFFECTED_MODULES[@]}"; do
    if is_backend_service "${m}"; then
      BACKEND_AFFECTED+=("${m}")
    fi
  done
  for key in "${CHANGED[@]}"; do
    case "${key}" in
      __admin__)  FRONTEND_AFFECTED+=("xfjy-admin") ;;
      __app__)    FRONTEND_AFFECTED+=("xfjy-h5") ;;
      __deploy_web__) FRONTEND_AFFECTED+=("xfjy-admin" "xfjy-h5") ;;
    esac
  done
  # 后端编排或运行镜像变更 → 全部后端容器都要重建
  case " ${CHANGED[*]:-} " in
    *" __deploy_app__ "*) BACKEND_AFFECTED=("${BACKEND_SERVICES[@]}") ;;
  esac
fi

BACKEND_AFFECTED=($(uniq_list "${BACKEND_AFFECTED[@]:-}"))
FRONTEND_AFFECTED=($(uniq_list "${FRONTEND_AFFECTED[@]:-}"))

# ---- 5. 还原未受影响模块的 jar（产物缓存） ----
# 说明：未受影响的模块本次不参与 mvn package，但其 jar 可能仍需要
#       （例如编排变更导致需要重建镜像时，Dockerfile 要 COPY 到 jar）。
RESTORED=()
for m in "${ALL_MODULES[@]}"; do
  case " ${AFFECTED_MODULES[*]:-} " in
    *" ${m} "*) continue ;;
  esac
  cached="${JAR_CACHE_DIR}/${m}-1.0-SNAPSHOT.jar"
  if [ -f "${cached}" ]; then
    mkdir -p "${m}/target"
    cp -f "${cached}" "${m}/target/${m}-1.0-SNAPSHOT.jar"
    RESTORED+=("${m}")
  fi
done
[ ${#RESTORED[@]} -gt 0 ] && log "从产物缓存还原 jar：${RESTORED[*]}"

# ---- 6. 输出结果 ----
BUILD_MODULES=($(uniq_list "${AFFECTED_MODULES[@]:-}"))

{
  printf 'BACKEND_AFFECTED="%s"\n' "${BACKEND_AFFECTED[*]:-}"
  printf 'FRONTEND_AFFECTED="%s"\n' "${FRONTEND_AFFECTED[*]:-}"
  printf 'BUILD_MODULES="%s"\n' "${BUILD_MODULES[*]:-}"
  printf 'FULL_BUILD="%s"\n' "${FULL_BUILD}"
} > "${AFFECTED_ENV}"

log "变更项：${CHANGED[*]:-（无）}"
log "受影响后端：${BACKEND_AFFECTED[*]:-（无）}"
log "受影响前端：${FRONTEND_AFFECTED[*]:-（无）}"
log "待构建模块：${BUILD_MODULES[*]:-（无）}"
log "结果已写入 ${AFFECTED_ENV}"

ok "增量范围判定完成"
