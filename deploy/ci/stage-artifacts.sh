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
#   Dockerfile  ← deploy/docker/Dockerfile.{admin,h5} 的副本
#   src/ 等前端源码（排除 node_modules / dist）
#   nginx.conf  ← deploy/docker/nginx.conf
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
# stage_frontend <服务名> <源码目录> <Dockerfile 名>
stage_frontend() {
  local svc="$1" src="$2" df="$3"
  [ -d "${src}" ] || return 1
  local dir="${STAGE_ROOT}/${svc}"
  rm -rf "${dir}"
  mkdir -p "${dir}"
  # 复制前端源码（排除 node_modules / dist / .git 等大目录）
  ( cd "${src}" && tar -cf - \
      --exclude=node_modules --exclude=dist --exclude=.git \
      --exclude=.workbuddy --exclude=unpackage --exclude=.hbuilderx \
      . ) | ( cd "${dir}" && tar -xf - )
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
for pair in "xfjy-admin:xfjy-admin:Dockerfile.admin" "xfjy-h5:xfjy-app:Dockerfile.h5"; do
  svc="${pair%%:*}"; rest="${pair#*:}"; src="${rest%%:*}"; df="${rest#*:}"
  if [ "${FRONTEND_ONLY}" = "true" ] || { [ ${#TARGETS[@]} -eq 0 ] && [ "${FRONTEND_ONLY}" != "true" ]; } \
     || printf '%s\n' "${TARGETS[@]}" | grep -qx "${svc}"; then
    if stage_frontend "${svc}" "${src}" "${df}"; then
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
