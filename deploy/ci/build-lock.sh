#!/bin/sh
# ============================================================
# 构建互斥锁：防止 ci.yml 与 deploy.yml 并发写同一 m2/npm 缓存卷
#
# 背景：act_runner 支持多槽位并发（实测 run101 的 3 个 ci job 与
#       run102 的 deploy job 时间窗重叠）。Gitea 并发组在本实例上
#       跨 workflow 会误杀对方 run（cancel-in-progress=false 也 cancel），
#       所以串行化只能靠文件锁。
#
# 用法（容器内，${CI_STATE_DIR}=/opt/ci-state 挂载卷）：
#   LOCK_RESOURCE=m2  sh deploy/ci/build-lock.sh acquire   # Maven 缓存锁
#   LOCK_RESOURCE=npm sh deploy/ci/build-lock.sh acquire   # npm 缓存锁
#   sh deploy/ci/build-lock.sh release                     # 释放（幂等）
#
# 资源域说明：ci 的 Maven job 只抢 m2、前端 job 只抢 npm（可并行）；
# deploy 两把都抢，固定顺序 m2→npm（ci 侧永远单锁，不存在环等待/死锁）。
#
# 陈旧锁回收：锁持有超过 STALE_AFTER 秒视为崩溃残留，强制接管。
# 超时：等待超过 TIMEOUT 秒仍拿不到 → exit 1（让 job 失败而非死等）。
# ============================================================
set -eu

LOCK_DIR="${CI_STATE_DIR:-/opt/ci-state}/build.lock.${LOCK_RESOURCE:-m2}"
TIMEOUT="${LOCK_TIMEOUT:-1800}"
STALE_AFTER="${LOCK_STALE_AFTER:-1500}"

case "${1:-}" in
  acquire)
    NOW="$(date +%s)"
    WAITED=0
    while ! mkdir "${LOCK_DIR}" 2>/dev/null; do
      START="$(cat "${LOCK_DIR}/started_at" 2>/dev/null || echo '')"
      NOWTS="$(date +%s)"
      if [ -n "${START}" ] && [ $((NOWTS - START)) -gt "${STALE_AFTER}" ]; then
        echo "[lock] 锁已持有 $((NOWTS - START))s（>${STALE_AFTER}s），判定陈旧，强制回收"
        rm -rf "${LOCK_DIR}"
        continue
      fi
      if [ "${WAITED}" -ge "${TIMEOUT}" ]; then
        echo "[lock] 等待锁超时（${TIMEOUT}s），持有者: $(cat "${LOCK_DIR}/owner" 2>/dev/null || echo '未知')"
        exit 1
      fi
      if [ "${WAITED}" -eq 0 ]; then
        echo "[lock] 锁被占用（$(cat "${LOCK_DIR}/owner" 2>/dev/null || echo '未知持有者')），排队等待..."
      fi
      sleep 5
      WAITED=$((WAITED + 5))
    done
    printf '%s\n' "${LOCK_OWNER:-unknown}" > "${LOCK_DIR}/owner"
    printf '%s\n' "$(date +%s)" > "${LOCK_DIR}/started_at"
    echo "[lock] 已获取构建锁（owner=${LOCK_OWNER:-unknown}）"
    ;;
  release)
    if [ -d "${LOCK_DIR}" ]; then
      echo "[lock] 释放构建锁（owner=$(cat "${LOCK_DIR}/owner" 2>/dev/null || echo '?')）"
      rm -rf "${LOCK_DIR}"
    fi
    ;;
  *)
    echo "usage: $0 acquire | release" >&2
    exit 2
    ;;
esac
