#!/usr/bin/env bash
# ============================================================
# Gitee → Gitea 代码同步（自动部署触发器）
#
# 作用：
#   定时把 Gitee 主仓库的 master 同步到本机 Gitea 仓库，
#   push 事件会触发 Gitea Actions 流水线 → 自动编译打包 → 部署 Docker 容器。
#
# 为什么用脚本而不是 Gitee Webhook：
#   Gitee 侧 webhook 需要 Personal Access Token 且第三方回调常被拦；
#   本方案只依赖公开仓库的 git 拉取，稳定可控、无需额外凭据。
#
# 部署位置：/home/itxinfei/mytanhua/deploy/sync-gitee-to-gitea.sh
# 定时任务：每 2 分钟执行一次（crontab）
# ============================================================
set -uo pipefail

REPO_DIR="${REPO_DIR:-/home/itxinfei/mytanhua}"
GITEE_REMOTE="gitea"
BRANCH="${BRANCH:-master}"
LOG_FILE="${LOG_FILE:-/home/itxinfei/logs/gitea-sync.log}"

mkdir -p "$(dirname "${LOG_FILE}")"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "${LOG_FILE}"
}

cd "${REPO_DIR}" || { log "ERROR: 无法进入 ${REPO_DIR}"; exit 1; }

# 1) 拉取 Gitee 最新 master
if ! git fetch origin "${BRANCH}" --quiet 2>>"${LOG_FILE}"; then
  log "ERROR: 从 Gitee 拉取 ${BRANCH} 失败（网络或仓库不可达）"
  exit 1
fi

# 1.1) 同时更新 Gitea 远端引用（本地可能领先或落后 Gitea）
git fetch "${GITEE_REMOTE}" "${BRANCH}" --quiet >>"${LOG_FILE}" 2>&1

GITEE_SHA="$(git rev-parse "origin/${BRANCH}" 2>/dev/null || echo "")"
GITEA_SHA="$(git rev-parse "${GITEE_REMOTE}/${BRANCH}" 2>/dev/null || echo "")"

if [ -z "${GITEE_SHA}" ]; then
  log "ERROR: 无法解析 origin/${BRANCH} 的提交号"
  exit 1
fi

# 注意：同步 push 后 gitea/master 会多出 CI 配置提交，SHA 必然不等于 GITEE_SHA，
#       用祖先检测（而非 SHA 相等）避免每轮 cron 重复触发无用同步。
if [ -n "${GITEA_SHA}" ] && git merge-base --is-ancestor "${GITEE_SHA}" "${GITEA_SHA}" 2>/dev/null; then
  # Gitee 最新提交已包含在 Gitea 历史中，无需同步
  exit 0
fi

log "检测到新提交：Gitee=${GITEE_SHA:0:8} Gitea=${GITEA_SHA:0:8}"

# 2) 同步到本地工作区
# 注意：用 checkout 而非 reset --hard，避免误删服务器上未提交的文件。
# 若工作区有本地改动导致 checkout 失败，直接放弃本次同步（下一轮 cron 会重试）。
if ! git checkout --force "${GITEE_SHA}" -- . >>"${LOG_FILE}" 2>&1; then
  log "WARN: 工作区存在冲突改动，本次同步跳过"
  exit 1
fi

# 2.1) 仅兜底恢复 Gitee 主仓不存在的 CI 专属文件（从 Gitea 远端）。
#      双端维护的文件（compose/manage.sh/setup.sh 等）一律以 Gitee 版本为准，
#      避免用 Gitea 旧版覆盖 Gitee 新版导致修改丢失。
CI_ONLY_PATHS=(
  ".gitea"
  "deploy/application-local.ci.yml"
)
CI_CHANGED=0
for p in "${CI_ONLY_PATHS[@]}"; do
  if ! git cat-file -e "${GITEE_SHA}:${p}" 2>/dev/null; then
    if git cat-file -e "${GITEA_SHA}:${p}" 2>/dev/null; then
      git checkout "${GITEA_SHA}" -- "${p}" >>"${LOG_FILE}" 2>&1
      CI_CHANGED=1
    fi
  fi
done
if [ "${CI_CHANGED}" = "1" ]; then
  git commit -m "chore(ci): 恢复 Gitea 侧 CI 专属配置" -- .gitea deploy/application-local.ci.yml >>"${LOG_FILE}" 2>&1
fi

# 3) 推送到 Gitea —— push 事件触发 Gitea Actions 流水线
#    Gitea 远端可能含本地没有的提交（如流水线自身产生的提交），先把它合并进来再推，
#    否则会因非快进被拒。
if [ -n "${GITEA_SHA}" ] && ! git merge-base --is-ancestor "${GITEA_SHA}" HEAD 2>/dev/null; then
  log "合并 Gitea 远端提交 ${GITEA_SHA:0:8}"
  if ! git merge --no-edit "${GITEA_SHA}" >>"${LOG_FILE}" 2>&1; then
    log "WARN: 合并 Gitea 远端提交冲突，以 Gitee 为准强制对齐"
    git reset --hard "${GITEE_SHA}" >>"${LOG_FILE}" 2>&1
  fi
fi

if git push "${GITEE_REMOTE}" "HEAD:refs/heads/${BRANCH}" >>"${LOG_FILE}" 2>&1; then
  log "已推送到 Gitea，流水线已触发：${GITEE_SHA:0:8}"
else
  log "ERROR: 推送到 Gitea 失败"
fi
