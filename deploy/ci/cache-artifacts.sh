#!/usr/bin/env bash
# ============================================================
# 心飞交友 - 构建产物缓存（增量编译用）
#
# 作用：把本次 mvn package 产出的 jar 存到持久化目录，
#       下次增量构建时，未变更模块的 jar 从这里还原，
#       使 Dockerfile 的 COPY ${SERVICE}/target/*.jar 始终有文件可拷。
#
# 用法：
#   bash deploy/ci/cache-artifacts.sh save    # 构建后：jar → 缓存
#   bash deploy/ci/cache-artifacts.sh save-fp # 构建后：写指纹基线
# ============================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CI_STATE_DIR="${CI_STATE_DIR:-/opt/ci-state}"
JAR_CACHE_DIR="${CI_STATE_DIR}/jars"
FINGERPRINT_DIR="${CI_STATE_DIR}/fingerprints"

ACTION="${1:-save}"

ALL_MODULES=(
  xfjy-gateway xfjy-sso xfjy-user-service xfjy-circle-service
  xfjy-video-service xfjy-match-service xfjy-message-service
  xfjy-api xfjy-server xfjy-recommend xfjy-elasticsearch xfjy-pay-service
)

log() { printf '%s[cache]%s %s\n' $'\033[36m' $'\033[0m' "$*"; }
ok()  { printf '%s[ OK ]%s %s\n' $'\033[32m' $'\033[0m' "$*"; }

mkdir -p "${JAR_CACHE_DIR}" "${FINGERPRINT_DIR}"

save_jars() {
  local saved=0 m
  for m in "${ALL_MODULES[@]}"; do
    local jar="${m}/target/${m}-1.0-SNAPSHOT.jar"
    if [ -f "${jar}" ]; then
      cp -f "${jar}" "${JAR_CACHE_DIR}/${m}-1.0-SNAPSHOT.jar"
      saved=$((saved + 1))
    fi
  done
  log "已缓存 jar：${saved} 个 → ${JAR_CACHE_DIR}"
}

save_fingerprints() {
  # 与 incremental-build.sh 保持一致的指纹算法（必须完全一致，否则基线失效）
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

  local m
  for m in "${ALL_MODULES[@]}"; do
    dir_fingerprint "${m}/src" "${m}/pom.xml" | sha256sum | cut -d' ' -f1 > "${FINGERPRINT_DIR}/${m}.fp"
  done
  sha256sum pom.xml | cut -d' ' -f1 > "${FINGERPRINT_DIR}/__root_pom__.fp"
  dir_fingerprint xfjy-admin/src xfjy-admin/package.json xfjy-admin/vite.config.js xfjy-admin/index.html | sha256sum | cut -d' ' -f1 > "${FINGERPRINT_DIR}/__admin__.fp"
  dir_fingerprint xfjy-app/src xfjy-app/package.json xfjy-app/vite.config.js xfjy-app/manifest.json | sha256sum | cut -d' ' -f1 > "${FINGERPRINT_DIR}/__app__.fp"
  dir_fingerprint deploy/docker deploy/docker-compose.web.yml | sha256sum | cut -d' ' -f1 > "${FINGERPRINT_DIR}/__deploy_web__.fp"
  dir_fingerprint deploy/docker deploy/docker-compose.app.yml | sha256sum | cut -d' ' -f1 > "${FINGERPRINT_DIR}/__deploy_app__.fp"

  git rev-parse HEAD > "${CI_STATE_DIR}/last-success-commit" 2>/dev/null || true
  log "已写入指纹基线 → ${FINGERPRINT_DIR}"
}

case "${ACTION}" in
  save)
    save_jars
    save_fingerprints
    ok "产物与指纹缓存完成"
    ;;
  save-jars)
    save_jars
    ok "jar 缓存完成"
    ;;
  save-fp)
    save_fingerprints
    ok "指纹基线完成"
    ;;
  *)
    echo "用法: $0 save|save-jars|save-fp" >&2
    exit 1
    ;;
esac
