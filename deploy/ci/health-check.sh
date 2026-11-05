#!/usr/bin/env bash
# ============================================================
# 心飞交友 - 部署后健康检查（CI 部署流水线调用）
#
# 检查项（失败只输出告警，不阻断流水线）：
#   1. 等待后端容器达到 healthy 状态（最多 60s）
#   2. 探测网关 /admin/ 接口是否可访问
#   3. 列出运行中的服务（供人工核对 Nacos 注册）
#
# 用法：bash deploy/ci/health-check.sh
# ============================================================
set -uo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

APP_COMPOSE="deploy/docker-compose.app.yml"
APP_PROJECT="xfjy-app"

echo "=== 等待容器健康（最多 60s）==="
for i in $(seq 1 30); do
  UNHEALTHY=$(docker compose -p "${APP_PROJECT}" -f "${APP_COMPOSE}" \
    ps --format json 2>/dev/null \
    | python3 -c "
import sys, json
unhealthy = []
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    c = json.loads(line)
    state = c.get('State', '')
    health = c.get('Health', '')
    if state != 'running' or (health and 'healthy' not in health.lower()):
        unhealthy.append(f\"  {c.get('Name','?')}: {state}/{health}\")
print('\n'.join(unhealthy) if unhealthy else '')
" 2>/dev/null || true)

  if [ -z "$UNHEALTHY" ]; then
    echo "[OK] 所有容器健康"
    break
  fi
  echo "  等待中 ($i/30)..."
  echo "$UNHEALTHY"
  sleep 2
done

echo ""
echo "=== 网关探测 ==="
# 网关端口 8090，探测 /admin/ 接口（应由 xfjy-server 路由转发）。
# 注意：HTTP 000 表示 curl 连接失败（容器未监听/网络问题），绝不能当成功；
# 只有 2xx/4xx/5xx 都算"网关在响应"。旧逻辑 `!= 000` 在 000 时死循环 15 次后
# 无输出且 exit 0（看似通过），run 401 就出现「HTTP 000000 + [OK]」的误报。
GATEWAY_OK=false
for i in $(seq 1 15); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://127.0.0.1:8090/admin/ 2>/dev/null || echo '000')
  if [ "$CODE" != "000" ]; then
    echo "[OK] 网关响应 HTTP $CODE"
    GATEWAY_OK=true
    break
  fi
  echo "  等待网关启动 ($i/15)... (HTTP $CODE)"
  sleep 2
done
if [ "${GATEWAY_OK}" != "true" ]; then
  echo "[WARN] 网关 15 次探测均无响应（HTTP 000），请检查容器状态"
fi

echo ""
echo "=== 网关路由匹配断言（防路由配置静默失效）==="
# SC 2025.x 前缀迁移类事故（07a220c）中容器 healthy、端口在听，但路由表为空 →
# 全 404。仅探测端口无法发现，必须断言已知路由能匹配到下游（非 404）。
# 选 /movements（xfjy-server 路由）与 /user/login（xfjy-sso 路由）两个锚点：
# 期望 200/400/401/403/500/503 等（只要不是 404 就说明路由已加载并转发）。
for probe in "movements" "user/login"; do
  PCODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:8090/${probe}" -H "Authorization: Bearer probe" 2>/dev/null || echo '000')
  if [ "$PCODE" = "404" ]; then
    echo "[FAIL] 路由未加载：/${probe} 返回 404（路由表可能为空，检查 server.webflux.routes 前缀）"
  elif [ "$PCODE" = "000" ]; then
    echo "[FAIL] 路由探测失败：/${probe} 连接失败（HTTP 000）"
  else
    echo "[OK] /${probe} => HTTP $PCODE（路由已加载并转发）"
  fi
done

echo ""
echo "=== 服务注册检查（Nacos）==="
REGISTERED=$(docker compose -p "${APP_PROJECT}" -f "${APP_COMPOSE}" \
  ps --format "{{.Names}}" 2>/dev/null | head -20)
echo "运行中的服务:"
echo "$REGISTERED"
echo ""
echo "[提示] 如需检查 Nacos 注册详情，访问 http://192.168.20.133:8080/"