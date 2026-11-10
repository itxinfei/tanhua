#!/usr/bin/env bash
# ============================================================
# xfjy-sso 单服务部署 + 并发登录验证
#
# 背景：tb_user.mobile 已加唯一索引 uk_user_mobile，UserService 已改为
#       「insert 撞唯一键 → 按手机号回查」。本脚本把这份代码部署到目标机，
#       并验证并发首次登录只产生一个账号。
#
# 用法（在目标机的仓库根目录执行）：
#   bash deploy/ci/deploy-sso.sh all              # 暂存 → 构建 → 重建 → 就绪 → 并发验证 → 清理
#   bash deploy/ci/deploy-sso.sh deploy           # 只做部署（不动数据）
#   bash deploy/ci/deploy-sso.sh verify           # 只做并发验证（可反复重跑）
#   bash deploy/ci/deploy-sso.sh verify --keep    # 验证后保留测试数据，只打印清理命令
#
# 复用现有约定，不另造一套：
#   - 暂存：deploy/ci/stage-artifacts.sh xfjy-sso（jar → deploy/build/xfjy-sso/app.jar + Dockerfile）
#   - compose：deploy/docker-compose.app.yml，工程名 xfjy-app，服务名 xfjy-sso
#   - 镜像：xfjy/xfjy-sso:${IMAGE_TAG}（默认 "<git 短 SHA>-p0fix"）
#   - 运行时镜像：deploy/docker/Dockerfile.java，jar 在镜像内固定为 /app/app.jar
#
# 只重建 xfjy-sso 一个容器（up -d --no-deps），不碰其他服务容器，不改 docker 全局配置。
#
# 副作用（来自 stage-artifacts.sh，不是本脚本引入）：
#   暂存指定服务后，stage-artifacts.sh 会 rm -rf 掉 deploy/build/ 下「本次未暂存」的
#   其他服务目录，避免残留旧 jar 被误用。所以跑完本脚本后，其他服务若要重新构建，
#   必须先重新执行一次对应的暂存，否则 compose 的 build context 会缺文件。
#
# 环境变量优先级（三级，逐键生效）：
#   1) 调用方已设置/export 的值        export IMAGE_TAG=xxx; bash deploy/ci/deploy-sso.sh all
#   2) deploy/.env 中的同名键          逐键读取，不 source，因此不会覆盖第 1 级
#   3) 脚本内默认值
#   本脚本只挑下面列出的键读 deploy/.env；其余键仍通过 docker compose --env-file 交给 compose。
#
# 可用环境变量（均有安全默认值）：
#   IMAGE_TAG            镜像标签，默认 "<git短SHA>-p0fix"；deploy/.env 里有值时会用 .env 的值
#   CI_STATE_DIR         jar 缓存根目录，默认 /opt/ci-state；缓存文件路径 <CI_STATE_DIR>/jars/<svc>-1.0-SNAPSHOT.jar
#   JAR_PATH             首选 jar，默认 xfjy-sso/target/xfjy-sso-1.0-SNAPSHOT.jar
#                        来源规则：JAR_PATH 存在则用它，否则用 CI_STATE_DIR 下的缓存 jar，两者都无才失败
#   EXPECTED_JAR_SHA256  可选；设置后与暂存后的 jar 摘要比对（仅部署前校验）
#   SSO_BASE_URL         探测地址，默认 http://127.0.0.1:18080（宿主机端口，不是容器内端口）
#   WAIT_SECONDS         等待就绪上限，默认 180
#   REDIS_HOST/PORT/PASSWORD   默认 127.0.0.1:6379，无密码
#   MYSQL_HOST/PORT/USERNAME/DB  默认 127.0.0.1:3306 / root / xfjy
#   MYSQL_PASSWORD       默认空。仓库里没有可复用的默认口令：
#                          · CI 模板 deploy/application-local.ci.yml 用的是它自身 spring.datasource.password
#                          · compose 里是 ${MYSQL_PASSWORD:-...} 的兜底值
#                        所以要让本脚本做 DB 断言与自动清理，请显式 export MYSQL_PASSWORD
#                        或在 deploy/.env 写入与上面一致的值；留空时只告警 + 打印清理命令。
#   TEST_PHONE           测试手机号，默认 19900001234，必须库中不存在
#   KEEP_TEST_DATA       1=验证后不清理
#
# 关于 healthy：compose 里 sso 的 healthcheck 是
#   pgrep -f 'xfjy-sso-1.0-SNAPSHOT.jar'，而镜像内 jar 固定为 /app/app.jar，
#   所以该 healthcheck 在当前镜像必然 unhealthy（既有问题，非本次代码引入）。
#   本脚本以「容器 Running + 端口可响应 + 登录接口可用」判定就绪，
#   health 状态仅告警，不作为失败条件。
# ============================================================
set -Eeuo pipefail

# ---------- 参数 ----------
MODE="${1:-all}"
shift || true
KEEP_TEST_DATA="${KEEP_TEST_DATA:-0}"
for arg in "$@"; do
  case "${arg}" in
    --keep) KEEP_TEST_DATA=1 ;;
    *) echo "未知参数：${arg}" >&2; exit 2 ;;
  esac
done
case "${MODE}" in
  all|deploy|verify) ;;
  *) echo "用法：bash deploy/ci/deploy-sso.sh [all|deploy|verify] [--keep]" >&2; exit 2 ;;
esac

# ---------- 路径与常量 ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

SERVICE="xfjy-sso"
PROJECT="xfjy-app"
COMPOSE_FILE="deploy/docker-compose.app.yml"
STAGE_DIR="deploy/build/${SERVICE}"
ENV_FILE="deploy/.env"

# ---------- 输出 ----------
c_info=$'\033[36m'; c_ok=$'\033[32m'; c_warn=$'\033[33m'; c_err=$'\033[31m'; c_off=$'\033[0m'
log()  { printf '%s[deploy]%s %s\n' "${c_info}" "${c_off}" "$*"; }
ok()   { printf '%s[ OK ]%s %s\n'   "${c_ok}"   "${c_off}" "$*"; }
warn() { printf '%s[WARN]%s %s\n'   "${c_warn}" "${c_off}" "$*"; }
err()  { printf '%s[FAIL]%s %s\n'   "${c_err}"  "${c_off}" "$*" >&2; }

step() { printf '\n%s== %s ==%s\n' "${c_info}" "$*" "${c_off}"; }
run()  { printf '+ %s\n' "$*"; "$@"; }

# ---------- 环境变量：调用方 > deploy/.env > 默认值 ----------
# 不 source .env：shell 的赋值语义会让 .env 覆盖调用方已 export 的值（例如 IMAGE_TAG）。
# 这里逐键读取，只在本脚本消费的变量上做三级兜底。
env_file_get() {
  local key="$1" line v
  [ -f "${ENV_FILE}" ] || return 1
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "${ENV_FILE}" 2>/dev/null | tail -n1)" || return 1
  [ -n "${line}" ] || return 1
  v="${line#*=}"
  v="${v%$'\r'}"
  case "${v}" in
    \"*\") v="${v#\"}"; v="${v%\"}" ;;
    \'*\') v="${v#\'}"; v="${v%\'}" ;;
  esac
  printf '%s' "${v}"
}

# env_or <变量名> <默认值>：写回同名变量并 export，来源记录到 ENV_SOURCE
ENV_SOURCE=""
env_or() {
  local var="$1" def="${2-}" cur="" v=""
  if declare -p "${var}" >/dev/null 2>&1; then cur="${!var}"; fi
  if [ -n "${cur}" ]; then
    printf -v "${var}" '%s' "${cur}"; ENV_SOURCE="调用方"
  elif v="$(env_file_get "${var}")" && [ -n "${v}" ]; then
    printf -v "${var}" '%s' "${v}"; ENV_SOURCE="${ENV_FILE}"
  else
    printf -v "${var}" '%s' "${def}"; ENV_SOURCE="默认值"
  fi
  export "${var}"
}

env_or CI_STATE_DIR "/opt/ci-state"
JAR_CACHE_JAR="${CI_STATE_DIR%/}/jars/${SERVICE}-1.0-SNAPSHOT.jar"
env_or JAR_PATH "${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar"
env_or SSO_BASE_URL "http://127.0.0.1:18080"
env_or REDIS_HOST "127.0.0.1"
env_or REDIS_PORT "6379"
env_or REDIS_PASSWORD ""
env_or MYSQL_HOST "127.0.0.1"
env_or MYSQL_PORT "3306"
env_or MYSQL_DB "xfjy"
env_or MYSQL_USERNAME "root"
env_or MYSQL_PASSWORD ""
env_or IMAGE_TAG ""
IMAGE_TAG_SOURCE="${ENV_SOURCE}"

# 仅这三个是测试参数，不参与 .env 读取，避免被部署环境的 .env 意外改写
WAIT_SECONDS="${WAIT_SECONDS:-180}"
TEST_PHONE="${TEST_PHONE:-19900001234}"
TEST_CODE="${TEST_CODE:-654321}"

# 镜像标签默认值：沿用 manage.sh 的「commit 短 SHA」约定，加后缀区分本次未提交的构建
if [ -z "${IMAGE_TAG}" ]; then
  GIT_SHA="$(git -C "${REPO_ROOT}" rev-parse --short HEAD 2>/dev/null || echo nogit)"
  IMAGE_TAG="${GIT_SHA}-p0fix"
  IMAGE_TAG_SOURCE="git 短 SHA"
fi
export IMAGE_TAG
if [ "${IMAGE_TAG_SOURCE}" = "${ENV_FILE}" ] && [ "${IMAGE_TAG}" = "latest" ]; then
  warn "IMAGE_TAG=latest 来自 ${ENV_FILE}：本次构建会覆盖 latest 标签。"
  warn "  要与旧镜像区分，请显式 export IMAGE_TAG=<tag> 后再跑（调用方优先于 ${ENV_FILE}）。"
fi

IMAGE="xfjy/${SERVICE}:${IMAGE_TAG}"
CONTAINER="${SERVICE}"

COMPOSE=(docker compose -p "${PROJECT}" -f "${COMPOSE_FILE}")
if [ -f "${ENV_FILE}" ]; then
  COMPOSE+=(--env-file "${ENV_FILE}")
fi

TMP_DIR=""
REFRESH_PID=""
CREATED_USER_ID=""
CREATED_TOKENS=()
DB_AVAILABLE=false
DOCKER_LOGS_PRINTED=false
JAR_SRC=""
SRC_SHA=""
STAGED_SHA=""

# ---------- 退出/报错处理 ----------
docker_logs_tail() {
  [ "${DOCKER_LOGS_PRINTED}" = true ] && return 0
  DOCKER_LOGS_PRINTED=true
  printf '\n--- docker logs --tail=200 %s ---\n' "${CONTAINER}" >&2
  docker logs --tail=200 "${CONTAINER}" >&2 2>&1 || true
  printf '\n--- compose ps ---\n' >&2
  "${COMPOSE[@]}" ps "${SERVICE}" >&2 2>&1 || true
}

on_error() {
  local rc=$? line=$1
  err "第 ${line} 行失败（exit=${rc}）"
  docker_logs_tail
  exit "${rc}"
}
trap 'on_error ${LINENO}' ERR

on_exit() {
  [ -n "${REFRESH_PID}" ] && kill "${REFRESH_PID}" >/dev/null 2>&1 || true
  if [ -n "${TMP_DIR}" ] && [ -d "${TMP_DIR}" ]; then rm -rf "${TMP_DIR}"; fi
}
trap on_exit EXIT

# ---------- Redis / MySQL 访问（优先本机客户端，回退到中间件容器） ----------
redis_c() {
  if command -v redis-cli >/dev/null 2>&1; then
    if [ -n "${REDIS_PASSWORD}" ]; then
      redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -a "${REDIS_PASSWORD}" --no-auth-warning "$@"
    else
      redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" "$@"
    fi
    return
  fi
  local c
  c="$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | awk 'tolower($2) ~ /redis/ {print $1; exit}')"
  [ -n "${c}" ] || return 1
  if [ -n "${REDIS_PASSWORD}" ]; then
    docker exec -e REDISCLI_AUTH="${REDIS_PASSWORD}" "${c}" redis-cli "$@"
  else
    docker exec "${c}" redis-cli "$@"
  fi
}

mysql_c() {
  local sql="$1"
  if command -v mysql >/dev/null 2>&1; then
    MYSQL_PWD="${MYSQL_PASSWORD}" mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" \
      -u"${MYSQL_USERNAME}" -N -B "${MYSQL_DB}" -e "${sql}"
    return
  fi
  # 回退到容器内客户端：此时 MYSQL_HOST/MYSQL_PORT 不生效，固定走容器内 127.0.0.1:3306
  local c
  c="$(docker ps --format '{{.Names}} {{.Image}}' 2>/dev/null | awk 'tolower($2) ~ /mysql/ {print $1; exit}')"
  [ -n "${c}" ] || return 1
  docker exec -e MYSQL_PWD="${MYSQL_PASSWORD}" "${c}" \
    mysql -h 127.0.0.1 -P 3306 -u"${MYSQL_USERNAME}" -N -B "${MYSQL_DB}" -e "${sql}"
}

detect_db() {
  if mysql_c "SELECT 1" >/dev/null 2>&1; then
    DB_AVAILABLE=true
    ok "MySQL 可访问（${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}，账号 ${MYSQL_USERNAME}）"
  else
    DB_AVAILABLE=false
    warn "MySQL 不可用：${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}，账号 ${MYSQL_USERNAME}（密码来源：${ENV_FILE} 或调用方 export）"
    warn "  后果：DB 行数断言跳过 → 唯一性未在库级验证；测试账号也不会自动删除。"
    warn "  如需完整校验：export MYSQL_PASSWORD=<与 deploy/application-local.ci.yml 一致的值> 后重跑。"
  fi
}

# ---------- jar 来源选择（JAR_PATH 优先，其次 CI_STATE_DIR 缓存） ----------
resolve_jar_source() {
  if [ -f "${JAR_PATH}" ]; then
    JAR_SRC="${JAR_PATH}"
  elif [ -f "${JAR_CACHE_JAR}" ]; then
    JAR_SRC="${JAR_CACHE_JAR}"
    log "仓库内无 target 产物，改用缓存 jar"
  else
    err "找不到 jar，两个来源都不存在："
    err "  - ${JAR_PATH}（仓库内构建产物，可用 JAR_PATH 覆盖）"
    err "  - ${JAR_CACHE_JAR}（缓存目录，可用 CI_STATE_DIR 覆盖根目录）"
    err "请先在目标机执行 mvn package，或把 jar 放进缓存目录后重跑。"
    return 1
  fi
  log "jar 来源：${JAR_SRC}"
  return 0
}

# ---------- 步骤：预检 ----------
preflight() {
  step "预检"
  [ -f "pom.xml" ] || { err "当前目录不是仓库根：$(pwd)"; exit 1; }
  command -v docker >/dev/null 2>&1 || { err "缺少 docker 命令"; exit 1; }
  docker compose version >/dev/null 2>&1 || { err "缺少 docker compose 插件"; exit 1; }
  [ -f "${COMPOSE_FILE}" ] || { err "缺少 ${COMPOSE_FILE}"; exit 1; }
  [ -f "deploy/ci/stage-artifacts.sh" ] || { err "缺少 deploy/ci/stage-artifacts.sh"; exit 1; }
  if [ "${MODE}" != "verify" ]; then
    resolve_jar_source || exit 1
    if [ -n "${EXPECTED_JAR_SHA256:-}" ]; then
      local now_sha
      now_sha="$(sha256sum "${JAR_SRC}" | awk '{print $1}')"
      [ "${now_sha}" = "${EXPECTED_JAR_SHA256}" ] || {
        err "jar 摘要与 EXPECTED_JAR_SHA256 不一致"
        err "  期望 ${EXPECTED_JAR_SHA256}"
        err "  实际 ${now_sha}  (${JAR_SRC})"
        exit 1
      }
      ok "jar 摘要与 EXPECTED_JAR_SHA256 一致"
    fi
  fi
  ok "docker / compose / 仓库路径检查通过"
  log "IMAGE_TAG=${IMAGE_TAG}（来源：${IMAGE_TAG_SOURCE}）  IMAGE=${IMAGE}  容器=${CONTAINER}"
  log "CI_STATE_DIR=${CI_STATE_DIR}  SSO_BASE_URL=${SSO_BASE_URL}"
}

# ---------- 步骤：暂存 ----------
stage() {
  step "暂存构建上下文"
  [ -n "${JAR_SRC}" ] || resolve_jar_source || exit 1
  run bash deploy/ci/stage-artifacts.sh "${SERVICE}"
  [ -f "${STAGE_DIR}/app.jar" ] || { err "暂存失败：${STAGE_DIR}/app.jar 不存在"; exit 1; }
  [ -f "${STAGE_DIR}/Dockerfile" ] || { err "暂存失败：${STAGE_DIR}/Dockerfile 不存在"; exit 1; }
  SRC_SHA="$(sha256sum "${JAR_SRC}" | awk '{print $1}')"
  STAGED_SHA="$(sha256sum "${STAGE_DIR}/app.jar" | awk '{print $1}')"
  log "源 jar     ${JAR_SRC}"
  log "源 jar     sha256=${SRC_SHA}  ($(stat -c '%y' "${JAR_SRC}" 2>/dev/null || date))"
  log "暂存 jar   sha256=${STAGED_SHA}"
  [ "${SRC_SHA}" = "${STAGED_SHA}" ] || {
    err "暂存 jar 与源 jar 不一致：stage-artifacts.sh 取的源与 JAR_PATH/缓存判定不同"
    exit 1
  }
  ok "暂存完成，jar 摘要一致"
}

# ---------- 步骤：构建镜像 ----------
build_image() {
  step "构建镜像 ${IMAGE}"
  run "${COMPOSE[@]}" build "${SERVICE}"
  docker image inspect "${IMAGE}" >/dev/null 2>&1 || { err "镜像不存在：${IMAGE}"; exit 1; }
  ok "镜像构建完成：${IMAGE}"
}

# ---------- 步骤：重建单个容器 ----------
up_service() {
  step "重建容器 ${CONTAINER}（--no-deps，不影响其他服务）"
  run "${COMPOSE[@]}" up -d --no-deps --force-recreate "${SERVICE}"
  ok "已提交重建：${CONTAINER}"
}

# ---------- 步骤：等待就绪 ----------
wait_ready() {
  step "等待服务就绪（上限 ${WAIT_SECONDS}s）"
  local deadline=$((SECONDS + WAIT_SECONDS)) code
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    if [ "$(docker inspect -f '{{.State.Running}}' "${CONTAINER}" 2>/dev/null || echo false)" = "true" ]; then
      code="$(curl -s -o /dev/null -m 3 -w '%{http_code}' "${SSO_BASE_URL}/" 2>/dev/null || true)"
      if [ -n "${code}" ] && [ "${code}" != "000" ]; then
        ok "端口已响应（GET ${SSO_BASE_URL}/ → HTTP ${code}，${SECONDS}s）"
        return 0
      fi
    fi
    sleep 3
  done
  err "等待超时：${WAIT_SECONDS}s 内未就绪"
  docker_logs_tail
  exit 1
}

# ---------- 步骤：部署态核对 ----------
check_deployed() {
  step "部署态核对"
  local running health actual_image
  running="$(docker inspect -f '{{.State.Running}}' "${CONTAINER}" 2>/dev/null || echo false)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${CONTAINER}" 2>/dev/null || echo none)"
  actual_image="$(docker inspect -f '{{.Config.Image}}' "${CONTAINER}" 2>/dev/null || echo none)"
  log "running=${running}  health=${health}  image=${actual_image}"
  [ "${running}" = "true" ] || { err "容器未运行"; docker_logs_tail; exit 1; }
  [ "${actual_image}" = "${IMAGE}" ] || { err "容器镜像与期望不一致：期望 ${IMAGE}，实际 ${actual_image}"; exit 1; }
  ok "容器运行中，镜像标签匹配"

  if [ "${health}" = "unhealthy" ]; then
    warn "容器 health=unhealthy：这是 compose 中 pgrep 'xfjy-sso-1.0-SNAPSHOT.jar' 与镜像内 /app/app.jar 不匹配导致的既有问题，不代表进程异常"
  fi

  local img_sha
  img_sha="$(docker run --rm --network none --entrypoint sha256sum "${IMAGE}" /app/app.jar | awk '{print $1}')"
  log "镜像内 /app/app.jar sha256=${img_sha}"
  [ "${img_sha}" = "${STAGED_SHA}" ] || { err "镜像内 jar 与暂存 jar 不一致，镜像来源可疑"; exit 1; }
  ok "镜像内 jar 与本次暂存 jar 一致（来源可追溯）"

  local started
  started="$(docker inspect -f '{{.State.StartedAt}}' "${CONTAINER}")"
  log "容器启动时间=${started}"
}

# ---------- 清理命令（DB 不可用 / 需人工兜底时打印） ----------
print_cleanup_commands() {
  local code_key="XFJY_SSO_CODE_${TEST_PHONE}"
  local fail_key="XFJY_SSO_CODE_FAIL_${TEST_PHONE}"
  local id="${CREATED_USER_ID:-<user.id>}"
  local t
  warn "手工清理命令（在目标机可直接执行；密码用你自己的，勿写入仓库）："
  warn "  # 1) 删除测试账号（幂等：只删该手机号对应的行）"
  warn "  mysql -h ${MYSQL_HOST} -P ${MYSQL_PORT} -u${MYSQL_USERNAME} -p ${MYSQL_DB} -e \"DELETE FROM tb_user WHERE mobile='${TEST_PHONE}';\""
  warn "  # 2) 删除验证码 / 失败计数键"
  warn "  redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} DEL ${code_key} ${fail_key}"
  warn "  # 3) 删除会话键（id=${id}）"
  warn "  redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} DEL Authorization:login:session:user-${id} Authorization:login:last-activity:user-${id}"
  for t in "${CREATED_TOKENS[@]:-}"; do
    [ -n "${t}" ] || continue
    warn "  redis-cli -h ${REDIS_HOST} -p ${REDIS_PORT} DEL Authorization:login:token:${t} Authorization:login:last-activity:${t}"
  done
  warn "  # 若没有本机客户端，可改用容器内客户端："
  warn "  docker exec -e MYSQL_PWD=<密码> xfjy-mysql mysql -h 127.0.0.1 -P 3306 -u${MYSQL_USERNAME} -N -B ${MYSQL_DB} -e \"DELETE FROM tb_user WHERE mobile='${TEST_PHONE}';\""
  warn "  docker exec xfjy-redis redis-cli DEL ${code_key} ${fail_key}"
}

# ---------- 验证：并发首次登录 ----------
verify_concurrency() {
  step "并发首次登录验证（手机号 ${TEST_PHONE}）"
  [[ "${TEST_PHONE}" =~ ^1[3-9][0-9]{9}$ ]] || { err "TEST_PHONE 格式不合法：${TEST_PHONE}"; exit 1; }

  detect_db
  local before_count="unknown"
  if [ "${DB_AVAILABLE}" = true ]; then
    before_count="$(mysql_c "SELECT COUNT(*) FROM tb_user WHERE mobile='${TEST_PHONE}'")"
    log "测试前该手机号在 tb_user 中的行数：${before_count}"
    [ "${before_count}" = "0" ] || { err "该手机号已存在（${before_count} 行），请换 TEST_PHONE 再跑，避免误删真实账号"; exit 1; }
  fi
  # 供清理阶段判断：只有确认「测试前不存在」才允许自动删除
  BEFORE_COUNT="${before_count}"

  local code_key="XFJY_SSO_CODE_${TEST_PHONE}"
  local fail_key="XFJY_SSO_CODE_FAIL_${TEST_PHONE}"
  redis_c SET "${code_key}" "${TEST_CODE}" EX 300 >/dev/null
  ok "已写入验证码键 ${code_key}（测试专用，验证码一次性语义在并发场景下靠本循环持续续期）"

  # 并发请求各自校验成功会删除验证码键，这里持续续期，隔离出「注册唯一性」这一被测点
  refresh_code() {
    while :; do
      redis_c SET "${code_key}" "${TEST_CODE}" EX 300 >/dev/null 2>&1 || true
      sleep 0.05
    done
  }
  refresh_code &
  REFRESH_PID=$!

  TMP_DIR="$(mktemp -d)"
  local i
  for i in $(seq 1 10); do
    (
      curl -sS -m 20 -o "${TMP_DIR}/body.${i}" -w '%{http_code}' \
        -X POST "${SSO_BASE_URL}/user/loginVerification" \
        -H 'Content-Type: application/json' \
        -H "X-Forwarded-For: 10.99.0.${i}" \
        -d "{\"phone\":\"${TEST_PHONE}\",\"code\":\"${TEST_CODE}\"}" \
        > "${TMP_DIR}/code.${i}" 2>"${TMP_DIR}/err.${i}" || true
    ) &
  done
  wait
  kill "${REFRESH_PID}" >/dev/null 2>&1 || true
  REFRESH_PID=""

  local fail=0
  local ids="" new_count=0
  for i in $(seq 1 10); do
    local c
    c="$(cat "${TMP_DIR}/code.${i}" 2>/dev/null || echo 000)"
    local body
    body="$(cat "${TMP_DIR}/body.${i}" 2>/dev/null || true)"
    printf '  #%-2s HTTP %-3s %s\n' "${i}" "${c}" "$(printf '%s' "${body}" | cut -c1-140)"
    [ "${c}" = "200" ] || fail=$((fail + 1))
    local id
    id="$(printf '%s' "${body}" | sed -n 's/.*"id":\([0-9]\{1,\}\).*/\1/p' | head -n1)"
    [ -n "${id}" ] && ids="${ids}${id}"$'\n'
    if printf '%s' "${body}" | grep -q '"isNew":true'; then
      new_count=$((new_count + 1))
    fi
    if printf '%s' "${body}" | grep -q '"token":"'; then
      CREATED_TOKENS+=("$(printf '%s' "${body}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p' | head -n1)")
    fi
  done

  [ "${fail}" -eq 0 ] || { err "有 ${fail} 个请求未返回 200"; docker_logs_tail; exit 1; }
  ok "10 个并发请求全部 HTTP 200"

  local uniq_ids uniq_count
  uniq_ids="$(printf '%s' "${ids}" | grep -v '^$' | sort -u)"
  uniq_count="$(printf '%s\n' "${uniq_ids}" | grep -c . || true)"
  log "返回的不同 user.id 数量：${uniq_count}  值：$(printf '%s' "${uniq_ids}" | tr '\n' ' ')"
  [ "${uniq_count}" = "1" ] || { err "并发首次登录产生了 ${uniq_count} 个不同账号"; exit 1; }
  [ "${new_count}" = "1" ] || { err "isNew=true 的响应应有 1 个，实际 ${new_count} 个"; exit 1; }
  CREATED_USER_ID="${uniq_ids}"
  ok "10 个并发请求命中同一个 user.id=${CREATED_USER_ID}，且只有 1 个 isNew=true"

  step "串行再登录一次，确认命中同一 id"
  redis_c SET "${code_key}" "${TEST_CODE}" EX 300 >/dev/null
  local serial_body serial_id
  serial_body="$(curl -sS -m 20 -X POST "${SSO_BASE_URL}/user/loginVerification" \
    -H 'Content-Type: application/json' -H 'X-Forwarded-For: 10.99.1.1' \
    -d "{\"phone\":\"${TEST_PHONE}\",\"code\":\"${TEST_CODE}\"}")"
  serial_id="$(printf '%s' "${serial_body}" | sed -n 's/.*"id":\([0-9]\{1,\}\).*/\1/p' | head -n1)"
  log "串行响应：$(printf '%s' "${serial_body}" | cut -c1-160)"
  [ "${serial_id}" = "${CREATED_USER_ID}" ] || { err "串行登录命中 id=${serial_id}，与并发结果不一致"; exit 1; }
  ok "串行登录命中同一 user.id=${CREATED_USER_ID}"

  if [ "${DB_AVAILABLE}" = true ]; then
    local db_count
    db_count="$(mysql_c "SELECT COUNT(*) FROM tb_user WHERE mobile='${TEST_PHONE}'")"
    log "测试后该手机号在 tb_user 中的行数：${db_count}"
    [ "${db_count}" = "1" ] || { err "DB 中该手机号应有 1 行，实际 ${db_count} 行"; exit 1; }
    ok "DB 侧确认：该手机号只有 1 行"
  else
    warn "唯一性未验证（DB 侧）：MySQL 不可用，未执行 SELECT COUNT(*) 断言"
    warn "  现有证据仅为接口层：10 个并发响应 user.id 唯一、isNew=true 仅 1 个、串行再登录命中同一 id"
    warn "  上述证据无法排除「库中存在第二个同手机号行」；补齐 MYSQL_* 后重跑 verify 可得到库级结论"
    print_cleanup_commands
  fi
}

# ---------- 清理 ----------
cleanup_test_data() {
  step "清理测试数据"
  local code_key="XFJY_SSO_CODE_${TEST_PHONE}"
  local fail_key="XFJY_SSO_CODE_FAIL_${TEST_PHONE}"

  local k
  for k in "${code_key}" "${fail_key}"; do
    redis_c DEL "${k}" >/dev/null 2>&1 || true
  done
  local t
  for t in "${CREATED_TOKENS[@]:-}"; do
    [ -n "${t}" ] || continue
    redis_c DEL "Authorization:login:token:${t}" "Authorization:login:last-activity:${t}" >/dev/null 2>&1 || true
  done
  if [ -n "${CREATED_USER_ID}" ]; then
    redis_c DEL "Authorization:login:session:user-${CREATED_USER_ID}" \
                 "Authorization:login:last-activity:user-${CREATED_USER_ID}" >/dev/null 2>&1 || true
  fi
  ok "Redis 验证码键 / token / session 已按已知键清理"

  if [ -z "${CREATED_USER_ID}" ]; then
    warn "未取得测试账号 id，跳过 DB 删除"
    return 0
  fi
  if [ "${DB_AVAILABLE}" != true ]; then
    warn "MySQL 不可用：测试账号未删除（唯一性亦未经 DB 验证）"
    print_cleanup_commands
    return 0
  fi
  if [ "${BEFORE_COUNT:-unknown}" != "0" ]; then
    warn "测试前未确认该手机号不存在（BEFORE_COUNT=${BEFORE_COUNT:-unknown}），为避免误删真实账号跳过自动删除"
    print_cleanup_commands
    return 0
  fi
  mysql_c "DELETE FROM tb_user WHERE id=${CREATED_USER_ID} AND mobile='${TEST_PHONE}'" >/dev/null 2>&1 || true
  local left
  left="$(mysql_c "SELECT COUNT(*) FROM tb_user WHERE mobile='${TEST_PHONE}'")"
  if [ "${left}" = "0" ]; then
    ok "测试账号已删除（id=${CREATED_USER_ID}）"
  else
    warn "测试账号残留 ${left} 行"
    print_cleanup_commands
  fi
}

# ---------- 主流程 ----------
main() {
  preflight
  if [ "${MODE}" = "all" ] || [ "${MODE}" = "deploy" ]; then
    stage
    build_image
    up_service
    wait_ready
    check_deployed
    # 供清理判断「测试前该手机号不存在」
    detect_db >/dev/null 2>&1 || true
    if [ "${DB_AVAILABLE}" = true ]; then
      BEFORE_COUNT="$(mysql_c "SELECT COUNT(*) FROM tb_user WHERE mobile='${TEST_PHONE}'" || echo unknown)"
    fi
  fi
  if [ "${MODE}" = "verify" ]; then
    step "容器状态核对（verify 模式）"
    local running health actual
    running="$(docker inspect -f '{{.State.Running}}' "${CONTAINER}" 2>/dev/null || echo false)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "${CONTAINER}" 2>/dev/null || echo none)"
    actual="$(docker inspect -f '{{.Config.Image}}' "${CONTAINER}" 2>/dev/null || echo none)"
    log "running=${running}  health=${health}  image=${actual}"
    [ "${running}" = "true" ] || { err "容器未运行"; docker_logs_tail; exit 1; }
    if [ "${actual}" = "${IMAGE}" ] && [ -f "${STAGE_DIR}/app.jar" ]; then
      local img_sha staged_sha
      img_sha="$(docker run --rm --network none --entrypoint sha256sum "${IMAGE}" /app/app.jar | awk '{print $1}')"
      staged_sha="$(sha256sum "${STAGE_DIR}/app.jar" | awk '{print $1}')"
      log "镜像 jar sha256=${img_sha}  暂存 jar sha256=${staged_sha}"
      [ "${img_sha}" = "${staged_sha}" ] || { err "镜像内 jar 与暂存 jar 不一致"; exit 1; }
      ok "镜像 tag 与 jar 来源一致"
    else
      warn "跳过 jar 来源比对（当前容器镜像 ${actual} 与 IMAGE_TAG=${IMAGE_TAG} 不同，或缺少 ${STAGE_DIR}/app.jar）；如需比对请用 deploy/all 模式"
    fi
  fi
  if [ "${MODE}" = "all" ] || [ "${MODE}" = "verify" ]; then
    verify_concurrency
    BEFORE_COUNT="${BEFORE_COUNT:-unknown}"
    if [ "${KEEP_TEST_DATA}" = "1" ]; then
      warn "KEEP_TEST_DATA=1：保留测试数据"
      print_cleanup_commands
    else
      cleanup_test_data
    fi
  fi

  printf '\n'
  ok "完成。IMAGE=${IMAGE}  CONTAINER=$(docker inspect -f '{{.State.Status}}' "${CONTAINER}" 2>/dev/null || echo unknown)"
}

main
