#!/usr/bin/env bash
# ============================================================
# 心飞交友 - 构建上下文暂存
#
# 作用：为每个服务准备一个「最小构建上下文」目录 deploy/build/<服务名>/，
#       让 compose 能以该目录为 context，避免每次 docker build 都传输整个仓库。
#
# 为什么需要：
#   Spring Boot 的 fat jar 每个约 70MB，前端 dist 也不小。
#   若 compose 的 build.context 指向仓库根，docker build 每次都要把整个仓库
#   （含所有 target/ 产物、前端 node_modules 等）传给 daemon，
#   11 个后端 + 2 个前端累计传输近 10GB，CI 里会长时间卡在 sending build context。
#   改为每服务一个只含必要文件的目录后，每次构建仅传几 MB ~ 70MB。
#
# 后端暂存目录内容：
#   app.jar     ← 该服务的 <svc>-1.0-SNAPSHOT.jar
#   Dockerfile  ← deploy/docker/Dockerfile.java 的副本
#
# 前端暂存目录内容：
#   dist/       ← 前端构建产物（CI 容器内已构建完成，见 deploy.yml「构建前端」步骤）
#   Dockerfile  ← deploy/docker/Dockerfile.{admin,h5} 的副本（单阶段 nginx + COPY dist）
#   nginx.conf  ← deploy/docker/nginx.conf
#
# 前端构建提速原理：
#   旧方式暂存源码、在 Docker build 内 npm ci —— 构建容器吃不到 CI 挂载的
#   /root/.npm 缓存卷，每次从零下载依赖 3-8 分钟。
#   新方式在 CI 容器内 npm ci --prefer-offline（走缓存卷秒级装依赖）+ npm run build，
#   本脚本只暂存现成 dist 产物，Docker build 退化为 COPY dist → nginx，秒级出镜像。
#
# 用法：
#   bash deploy/ci/stage-artifacts.sh                    # 暂存所有服务
#   bash deploy/ci/stage-artifacts.sh xfjy-server ...    # 只暂存指定后端服务
#   bash deploy/ci/stage-artifacts.sh --frontend-only    # 只暂存前端
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CI_STATE_DIR="${CI_STATE_DIR:-/opt/ci-state}"
JAR_CACHE_DIR="${CI_STATE_DIR}/jars"
STAGE_ROOT="deploy/build"

BACKEND_SERVICES=(
  xfjy-gateway xfjy-sso xfjy-user-service xfjy-circle-service
  xfjy-video-service xfjy-match-service xfjy-message-service
  xfjy-server xfjy-recommend xfjy-elasticsearch xfjy-pay-service
)

log() { printf '%s[stage]%s %s\n' $'\033[36m' $'\033[0m' "$*"; }
ok()  { printf '%s[ OK ]%s %s\n' $'\033[32m' $'\033[0m' "$*"; }
warn(){ printf '%s[WARN]%s %s\n' $'\033[33m' $'\033[0m' "$*"; }

FRONTEND_ONLY=false
TARGETS=()
for arg in "$@"; do
  case "${arg}" in
    --frontend-only) FRONTEND_ONLY=true ;;
    *) TARGETS+=("${arg}") ;;
  esac
done
[ ${#TARGETS[@]} -eq 0 ] && TARGETS=("${BACKEND_SERVICES[@]}")

# ---- 后端 ----
stage_backend() {
  local svc="$1"
  local jar=""
  if [ -f "${svc}/target/${svc}-1.0-SNAPSHOT.jar" ]; then
    jar="${svc}/target/${svc}-1.0-SNAPSHOT.jar"
  elif [ -f "${JAR_CACHE_DIR}/${svc}-1.0-SNAPSHOT.jar" ]; then
    jar="${JAR_CACHE_DIR}/${svc}-1.0-SNAPSHOT.jar"
  else
    return 1
  fi
  local dir="${STAGE_ROOT}/${svc}"
  mkdir -p "${dir}"
  cp -f "${jar}" "${dir}/app.jar"
  cp -f "deploy/docker/Dockerfile.java" "${dir}/Dockerfile"
  return 0
}

# ---- 前端 ----
# stage_frontend <服务名> <dist产物目录> <Dockerfile 名>
# 只暂存现成构建产物（前端构建已在 CI 容器内完成）。
stage_frontend() {
  local svc="$1" dist="$2" df="$3"
  if [ ! -f "${dist}/index.html" ]; then
    warn "${svc}: 缺少构建产物 ${dist}/index.html（前端未构建？CI 应先执行「构建前端」步骤）"
    return 1
  fi
  local dir="${STAGE_ROOT}/${svc}"
  rm -rf "${dir}"
  mkdir -p "${dir}/dist"
  # 复制构建产物到统一位置（Dockerfile 统一 COPY dist/ → nginx html）
  cp -a "${dist}/." "${dir}/dist/"
  cp -f "deploy/docker/${df}" "${dir}/Dockerfile"
  cp -f "deploy/docker/nginx.conf" "${dir}/nginx.conf"
  return 0
}

STAGED=()
MISSING=()

if [ "${FRONTEND_ONLY}" != "true" ]; then
  for svc in "${TARGETS[@]}"; do
    if stage_backend "${svc}"; then
      STAGED+=("${svc}")
    else
      MISSING+=("${svc}")
    fi
  done
  # 清理本次未涉及的后端目录，避免残留旧 jar 被误用
  for svc in "${BACKEND_SERVICES[@]}"; do
    skip=false
    for s in "${STAGED[@]}"; do
      [ "$s" = "${svc}" ] && skip=true && break
    done
    [ "${skip}" = false ] && rm -rf "${STAGE_ROOT}/${svc}"
  done
fi

# ---- 前端 ----
# pair 格式：<服务名>:<dist产物目录>:<Dockerfile 名>
for pair in "xfjy-admin:xfjy-admin/dist:Dockerfile.admin" "xfjy-h5:xfjy-app/dist/build/h5:Dockerfile.h5"; do
  svc="${pair%%:*}"; rest="${pair#*:}"; dist_dir="${rest%%:*}"; df="${rest#*:}"
  if [ "${FRONTEND_ONLY}" = "true" ] || { [ ${#TARGETS[@]} -eq 0 ] && [ "${FRONTEND_ONLY}" != "true" ]; } \
     || printf '%s\n' "${TARGETS[@]}" | grep -qx "${svc}"; then
    if stage_frontend "${svc}" "${dist_dir}" "${df}"; then
      STAGED+=("${svc}")
    else
      MISSING+=("${svc}")
    fi
  fi
done

[ ${#STAGED[@]} -gt 0 ] && log "已暂存构建上下文：${STAGED[*]}"
[ ${#MISSING[@]} -gt 0 ] && warn "以下服务缺少产物（未暂存）：${MISSING[*]}"
[ ${#STAGED[@]} -gt 0 ] && ok "构建上下文暂存完成"
exit 0
