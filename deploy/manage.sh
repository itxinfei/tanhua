#!/usr/bin/env bash
# ============================================================
# 心飞交友 - 统一部署管理脚本
#
# 覆盖三组 Docker 编排：
#   - 后端微服务（11 个 Spring Boot）：deploy/docker-compose.app.yml  → 工程名 xfjy-app
#   - 后台管理系统 admin（Vue3）      ：deploy/docker-compose.web.yml  → 工程名 xfjy-web
#   - App H5（uni-app）              ：deploy/docker-compose.web.yml  → 工程名 xfjy-web
#
# 用法：
#   bash deploy/manage.sh up                      # 启动全部
#   bash deploy/manage.sh up xfjy-gateway         # 启动单个服务
#   bash deploy/manage.sh up xfjy-gateway xfjy-admin   # 启动多个（可跨前后端）
#   bash deploy/manage.sh down                    # 停止全部
#   bash deploy/manage.sh down xfjy-video-service # 停止单个
#   bash deploy/manage.sh restart xfjy-server     # 重启单个
#   bash deploy/manage.sh status                  # 查看全部状态
#   bash deploy/manage.sh logs xfjy-server        # 跟踪日志
#   bash deploy/manage.sh build xfjy-admin        # 只构建镜像不启动
#   bash deploy/manage.sh deploy                  # 重新构建并启动全部（CI 用）
#
# 说明：
#   - 密码等敏感项从 deploy/.env 读取（该文件被 .gitignore 排除，不入库）；
#     文件不存在时退回 compose 内置默认值（仅适合本地/测试）。
#   - 后端服务用 network_mode: host，直接复用宿主机已映射的中间件端口。
#   - 固定 compose 工程名（-p xfjy-app / -p xfjy-web），重复执行只会重建变化的服务。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

APP_COMPOSE="deploy/docker-compose.app.yml"
WEB_COMPOSE="deploy/docker-compose.web.yml"
APP_PROJECT="xfjy-app"
WEB_PROJECT="xfjy-web"
ENV_FILE="deploy/.env"

# ---- 服务清单 ----
BACKEND_SERVICES=(
  xfjy-gateway
  xfjy-sso
  xfjy-user-service
  xfjy-circle-service
  xfjy-video-service
  xfjy-match-service
  xfjy-message-service
  xfjy-server
  xfjy-recommend
  xfjy-elasticsearch
  xfjy-pay-service
)
WEB_SERVICES=(
  xfjy-admin
  xfjy-h5
)
ALL_SERVICES=("${BACKEND_SERVICES[@]}" "${WEB_SERVICES[@]}")

RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; CYAN=$'\033[36m'; RESET=$'\033[0m'
log()  { printf '%s[manage]%s %s\n' "${CYAN}" "${RESET}" "$*"; }
ok()   { printf '%s[ OK ]%s %s\n' "${GREEN}" "${RESET}" "$*"; }
warn() { printf '%s[WARN]%s %s\n' "${YELLOW}" "${RESET}" "$*"; }
err()  { printf '%s[FAIL]%s %s\n' "${RED}" "${RESET}" "$*" >&2; }

usage() {
  sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

# 判断服务属于哪套编排
belongs_to() {
  local svc="$1" group="$2" item
  local -a list=()
  if [ "${group}" = "app" ]; then list=("${BACKEND_SERVICES[@]}"); else list=("${WEB_SERVICES[@]}"); fi
  for item in "${list[@]}"; do
    [ "${item}" = "${svc}" ] && return 0
  done
  return 1
}

# 组装 compose 命令前缀（自动带上 env-file）
compose_cmd() {
  local project="$1" file="$2"
  if [ -f "${ENV_FILE}" ]; then
    echo "docker compose -p ${project} -f ${file} --env-file ${ENV_FILE}"
  else
    echo "docker compose -p ${project} -f ${file}"
  fi
}

# 按服务名分组，分别落到 app / web 两套编排
resolve_targets() {
  local -a targets=("$@")
  if [ ${#targets[@]} -eq 0 ]; then
    APP_TARGETS=("${BACKEND_SERVICES[@]}")
    WEB_TARGETS=("${WEB_SERVICES[@]}")
    return 0
  fi
  APP_TARGETS=()
  WEB_TARGETS=()
  local svc
  for svc in "${targets[@]}"; do
    if belongs_to "${svc}" app; then
      APP_TARGETS+=("${svc}")
    elif belongs_to "${svc}" web; then
      WEB_TARGETS+=("${svc}")
    else
      err "未知服务名：${svc}"
      err "可用服务：${ALL_SERVICES[*]}"
      exit 1
    fi
  done
}

run_compose() {
  # run_compose <action> <group> [extra compose flags...] -- <services...>
  local action="$1" group="$2"; shift 2
  local -a extra=() services=()
  local seen_sep=false
  for arg in "$@"; do
    if [ "${arg}" = "--" ]; then seen_sep=true; continue; fi
    if [ "${seen_sep}" = true ]; then services+=("${arg}"); else extra+=("${arg}"); fi
  done

  local project file
  if [ "${group}" = "app" ]; then
    project="${APP_PROJECT}"; file="${APP_COMPOSE}"; services=("${APP_TARGETS[@]}")
  else
    project="${WEB_PROJECT}"; file="${WEB_COMPOSE}"; services=("${WEB_TARGETS[@]}")
  fi
  [ ${#services[@]} -eq 0 ] && return 0

  local cmd
  cmd="$(compose_cmd "${project}" "${file}") ${action} ${extra[*]} ${services[*]}"
  log "执行：${cmd}"
  # shellcheck disable=SC2086
  ${cmd}
}

do_status() {
  log "后端微服务（工程 ${APP_PROJECT}）"
  docker compose -p "${APP_PROJECT}" -f "${APP_COMPOSE}" ps 2>/dev/null || true
  log "前端（工程 ${WEB_PROJECT}）"
  docker compose -p "${WEB_PROJECT}" -f "${WEB_COMPOSE}" ps 2>/dev/null || true
  log "全部相关容器"
  docker ps --filter "name=xfjy-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" || true
}

do_logs() {
  local svc="${1:-}"
  if [ -z "${svc}" ]; then
    err "logs 需要指定服务名，例如：bash deploy/manage.sh logs xfjy-server"
    exit 1
  fi
  resolve_targets "${svc}"
  if [ ${#APP_TARGETS[@]} -gt 0 ]; then
    docker compose -p "${APP_PROJECT}" -f "${APP_COMPOSE}" logs -f --tail=200 "${APP_TARGETS[@]}"
  fi
  if [ ${#WEB_TARGETS[@]} -gt 0 ]; then
    docker compose -p "${WEB_PROJECT}" -f "${WEB_COMPOSE}" logs -f --tail=200 "${WEB_TARGETS[@]}"
  fi
}

main() {
  local cmd="${1:-help}"; shift || true
  case "${cmd}" in
    up|start)
      resolve_targets "$@"
      run_compose "up -d" app
      run_compose "up -d" web
      ok "已启动：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    down|stop)
      resolve_targets "$@"
      run_compose "stop" app
      run_compose "stop" web
      ok "已停止：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    restart)
      resolve_targets "$@"
      run_compose "restart" app
      run_compose "restart" web
      ok "已重启：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    rm)
      resolve_targets "$@"
      run_compose "rm -f" app
      run_compose "rm -f" web
      ok "已删除容器：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    build)
      resolve_targets "$@"
      run_compose "build" app
      run_compose "build" web
      ok "已构建镜像：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    deploy)
      # 重新构建并启动（CI 部署入口）
      resolve_targets "$@"
      run_compose "up -d --build" app
      run_compose "up -d --build" web
      ok "部署完成：${APP_TARGETS[*]} ${WEB_TARGETS[*]}"
      ;;
    status|ps)
      do_status
      ;;
    logs)
      do_logs "$@"
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      err "未知命令：${cmd}"
      usage
      exit 1
      ;;
  esac
}

main "$@"
