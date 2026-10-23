#!/usr/bin/env bash
#
# 心飞交友（xfjy）端到端冒烟测试脚本
#
# 说明：
#   本脚本从边缘网关(默认 8080)发起请求，覆盖完整微服务链路：
#     gateway -> sso(登录) -> server(聚合) -> 各拆分服务(user/match/circle/video/message，内部直连)
#   用于中间件(MySQL/Redis/MongoDB/Elasticsearch/RocketMQ)与全套微服务均已启动的环境中做冒烟验证。
#
# 前置依赖：curl（解析 token 仅用 grep/sed，无需 jq）
#
# 用法：
#   1) 带已有 token 直接跑： TOKEN=xxxx bash scripts/e2e-test.sh
#   2) 自动登录（需短信验证码或从服务端日志取得 code）： PHONE=13800000000 CODE=123456 bash scripts/e2e-test.sh
#   3) 自定义网关地址： GATEWAY=http://192.168.1.10:8080 TOKEN=xxxx bash scripts/e2e-test.sh
#
GATEWAY="${GATEWAY:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
PHONE="${PHONE:-13800000000}"
CODE="${CODE:-123456}"

FAIL=0
BODY_FILE="$(mktemp /tmp/xfjy_e2e.XXXXXX)"
http_code=0

pass() { echo -e "\033[32m[PASS]\033[0m $1"; }
fail() { echo -e "\033[31m[FAIL]\033[0m $1 (http=$2)"; FAIL=$((FAIL + 1)); }
info() { echo -e "\033[36m[INFO]\033[0m $1"; }

api() {
  local method="$1" path="$2" body="${3:-}"
  local url="$GATEWAY$path"
  local -a args=(-s -o "$BODY_FILE" -w '%{http_code}' -X "$method" --connect-timeout 5 --max-time 20)
  [ -n "$TOKEN" ] && args+=(-H "Authorization: $TOKEN")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  http_code=$(curl "${args[@]}" "$url" 2>/dev/null)
  [ -z "$http_code" ] && http_code=000
}

expect_ok() {
  local label="$1"
  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    pass "$label -> $http_code"
  else
    fail "$label" "$http_code"
    echo "  body: $(head -c 300 "$BODY_FILE" 2>/dev/null)"
  fi
}

tcp_ok() {
  (exec 3<>/dev/tcp/127.0.0.1/$1) 2>/dev/null && { exec 3>&-; return 0; } || return 1
}

echo "=============================================="
echo " 心飞交友 端到端冒烟测试"
echo " gateway = $GATEWAY"
echo "=============================================="

info "1. 服务端口存活检查"
for p in 8080 18080 18081 18090 18082 18084 18085 18086; do
  if tcp_ok "$p"; then pass "port $p 监听中"; else fail "port $p 未监听" "-"; fi
done

info "2. 登录获取 token"
if [ -z "$TOKEN" ]; then
  api POST /user/sendCode "{\"phone\":\"$PHONE\"}"
  info "sendCode -> $http_code (验证码需从短信或服务端日志获取)"
  api POST /user/loginVerification "{\"phone\":\"$PHONE\",\"code\":\"$CODE\"}"
  if [ "$http_code" = "200" ]; then
    TOKEN=$(grep -o '"token":"[^"]*"' "$BODY_FILE" | head -1 | sed 's/"token":"//;s/"//')
    if [ -n "$TOKEN" ]; then pass "登录成功, token 长度=${#TOKEN}"; else fail "loginVerification 返回无 token 字段" "$http_code"; fi
  else
    fail "loginVerification" "$http_code"
  fi
else
  info "已通过环境变量 TOKEN 注入，跳过登录"
fi

if [ -z "$TOKEN" ]; then
  info "无有效 TOKEN：仅执行无需鉴权的只读接口，需鉴权接口将报 401（可重新以 TOKEN=xxx 运行）"
fi

info "3. 核心业务链路"
api GET /xfjy/todayBest;          expect_ok "GET /xfjy/todayBest (今日佳人, 命中 match-service)"
api GET /xfjy/recommendation;     expect_ok "GET /xfjy/recommendation (推荐列表)"
api GET /xfjy/cards;              expect_ok "GET /xfjy/cards (探花卡片)"
api GET /users;                   expect_ok "GET /users (用户信息, 命中 user-service)"
api POST /movements "{\"text\":\"端到端冒烟测试动态\",\"longitude\":121.512253,\"latitude\":31.24094,\"locationName\":\"上海\"}"
expect_ok "POST /movements (发布动态, 命中 circle-service + RocketMQ)"
api GET /movements;              expect_ok "GET /movements (动态列表)"
api GET /huanxin/user;           expect_ok "GET /huanxin/user (环信用户, 命中 message-service)"
api POST /messages/contacts "{\"userId\":2}"; expect_ok "POST /messages/contacts (添加联系人, 命中 message-service)"
api GET /messages/contacts;      expect_ok "GET /messages/contacts (联系人列表)"
api GET /messages/announcements; expect_ok "GET /messages/announcements (系统公告)"

echo "=============================================="
if [ "$FAIL" -eq 0 ]; then
  echo -e "\033[32mE2E SMOKE PASS (全部通过)\033[0m"
  rm -f "$BODY_FILE"
  exit 0
else
  echo -e "\033[31mE2E SMOKE FAIL: $FAIL 项未通过\033[0m"
  rm -f "$BODY_FILE"
  exit 1
fi
