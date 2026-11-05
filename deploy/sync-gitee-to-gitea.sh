#!/usr/bin/env bash
# ============================================================
# Gitee -> Gitea 代码同步（自动部署触发器）
#
# 作用：定时把 Gitee 主仓库的 master 同步到本机 Gitea 仓库，
#       push 事件会触发 Gitea Actions 流水线 -> 自动编译打包 -> 部署 Docker 容器。
#
# 为什么用脚本而不是 Gitee Webhook：
#   Gitee 侧 webhook 需要 Personal Access Token 且第三方回调常被拦；
#   本方案只依赖公开仓库的 git 拉取，稳定可控、无需额外凭据。
#
# 部署位置：/home/itxinfei/mytanhua/deploy/sync-gitee-to-gitea.sh
# 定时任务：每 2 分钟执行一次（crontab）
#
# ============================================================
# 仓库与鉴权事实（2026-11-05 实测核对，修正了 CHANGELOG 的过时说法）：
#   * Gitea 真实仓库名为 admin/xfjy（admin/mytanhua 仅是 301 别名，不要改远端路径）。
#   * gitea remote 必须带 admin 的 API Token，否则 git 走匿名访问，
#     Gitea 对 git-receive-pack 返回 403，导致同步静默失败、CI 不触发。
#   * 推荐用环境变量 GITEA_REMOTE_URL 注入带 token 的地址，避免把 token 写死在 .git/config。
#     例：GITEA_REMOTE_URL="http://admin:<TOKEN>@192.168.20.133:3000/admin/xfjy.git"
# ============================================================
set -uo pipefail

REPO_DIR="${REPO_DIR:-/home/itxinfei/mytanhua}"
BRANCH="${BRANCH:-master}"
LOG_FILE="${LOG_FILE:-/home/itxinfei/logs/gitea-sync.log}"
# 可选：覆盖 Gitea 远端地址（含 token + 正确仓库路径 admin/xfjy.git）。
# 设置后脚本会幂等地把 gitea 远端指向它，无需手工改 .git/config。
GITEA_REMOTE_URL="${GITEA_REMOTE_URL:-}"
GITEA_REMOTE_NAME="gitea"

mkdir -p "$(dirname "${LOG_FILE}")"

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "${LOG_FILE}"
}

die() {
  log "ERROR: $*"
  echo "$(date '+%Y-%m-%d %H:%M:%S') ERROR: $*" >&2
  exit 1
}

cd "${REPO_DIR}" || die "无法进入 ${REPO_DIR}"

# 若提供 GITEA_REMOTE_URL，则确保 gitea 远端指向它（幂等）
if [ -n "${GITEA_REMOTE_URL}" ]; then
  if git remote get-url "${GITEA_REMOTE_NAME}" >/dev/null 2>&1; then
    git remote set-url "${GITEA_REMOTE_NAME}" "${GITEA_REMOTE_URL}" >>"${LOG_FILE}" 2>&1
  else
    git remote add "${GITEA_REMOTE_NAME}" "${GITEA_REMOTE_URL}" >>"${LOG_FILE}" 2>&1
  fi
fi

# 1) 拉取 Gitee 最新 master
if ! git fetch origin "${BRANCH}" --quiet 2>>"${LOG_FILE}"; then
  die "从 Gitee 拉取 ${BRANCH} 失败（网络或仓库不可达）"
fi

# 1.1) 更新 Gitea 远端引用；fetch 失败必须显式诊断，不再静默忽略
if ! git fetch "${GITEA_REMOTE_NAME}" "${BRANCH}" --quiet >>"${LOG_FILE}" 2>&1; then
  if grep -qiE "403|401|authentication|permission|access denied" "${LOG_FILE}"; then
    log "ERROR: 访问 Gitea 被拒绝（401/403）。原因通常是 gitea 远端缺少或使用了过期/无效的 admin API Token，git 走了匿名访问。请在 Gitea 重新生成 admin 的 API Token，并：①设置环境变量 GITEA_REMOTE_URL，或 ②执行 git remote set-url gitea 'http://admin:<新TOKEN>@192.168.20.133:3000/admin/xfjy.git'。"
  elif grep -qiE "404|not found|repository" "${LOG_FILE}"; then
    log "ERROR: Gitea 仓库路径不存在（404）。当前远端：$(git remote get-url "${GITEA_REMOTE_NAME}" 2>/dev/null)。真实仓库名应为 admin/xfjy.git（admin/mytanhua 仅是 301 别名），请修正路径。"
  elif grep -qiE "could not resolve|timed out|connection|refused" "${LOG_FILE}"; then
    log "ERROR: 无法连接 Gitea 主机（网络不可达）。请检查 192.168.20.133:3000 是否在线、防火墙是否放行。"
  else
    log "ERROR: git fetch ${GITEA_REMOTE_NAME} 失败，详见日志。"
  fi
  exit 1
fi

GITEE_SHA="$(git rev-parse "origin/${BRANCH}" 2>/dev/null || echo "")"
GITEA_SHA="$(git rev-parse "${GITEA_REMOTE_NAME}/${BRANCH}" 2>/dev/null || echo "")"

if [ -z "${GITEE_SHA}" ]; then
  die "无法解析 origin/${BRANCH} 的提交号"
fi

# 祖先检测：Gitee 最新提交已包含在 Gitea 历史中则跳过，避免每轮 cron 重复触发
if [ -n "${GITEA_SHA}" ] && git merge-base --is-ancestor "${GITEE_SHA}" "${GITEA_SHA}" 2>/dev/null; then
  exit 0
fi

log "检测到新提交：Gitee=${GITEE_SHA:0:8} Gitea=${GITEA_SHA:0:8}"

# 2) 同步到本地工作区（checkout 而非 reset --hard，避免误删未提交文件）
if ! git checkout --force "${GITEE_SHA}" -- . >>"${LOG_FILE}" 2>&1; then
  log "WARN: 工作区存在冲突改动，本次同步跳过"
  exit 1
fi

# 2.1) 兜底恢复 Gitee 主仓不存在的 CI 专属文件（从 Gitea 远端）
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

# 3) 推送：合并 Gitea 远端可能有的本地没有的提交（如流水线自身产生的提交）
if [ -n "${GITEA_SHA}" ] && ! git merge-base --is-ancestor "${GITEA_SHA}" HEAD 2>/dev/null; then
  log "合并 Gitea 远端提交 ${GITEA_SHA:0:8}"
  if ! git merge --no-edit "${GITEA_SHA}" >>"${LOG_FILE}" 2>&1; then
    log "WARN: 合并 Gitea 远端提交冲突，以 Gitee 为准强制对齐"
    git reset --hard "${GITEE_SHA}" >>"${LOG_FILE}" 2>&1
  fi
fi

if git push "${GITEA_REMOTE_NAME}" "HEAD:refs/heads/${BRANCH}" >>"${LOG_FILE}" 2>&1; then
  log "已推送到 Gitea，流水线已触发：${GITEE_SHA:0:8}"
else
  if grep -qiE "403|401|authentication|permission|access denied" "${LOG_FILE}"; then
    log "ERROR: 推送到 Gitea 被拒绝（401/403）。gitea 远端缺少有效 admin API Token。重新生成 token 后设置 GITEA_REMOTE_URL 或 git remote set-url gitea 'http://admin:<TOKEN>@192.168.20.133:3000/admin/xfjy.git'。"
  elif grep -qiE "404|not found" "${LOG_FILE}"; then
    log "ERROR: 推送失败（404）。仓库路径可能错误，真实路径为 admin/xfjy.git。"
  elif grep -qiE "non-fast-forward|rejected" "${LOG_FILE}"; then
    log "ERROR: 推送被拒（非快进）。Gitea 远端存在未被祖先检测覆盖的提交，请手动检查。"
  else
    log "ERROR: 推送到 Gitea 失败，详见日志。"
  fi
  exit 1
fi
