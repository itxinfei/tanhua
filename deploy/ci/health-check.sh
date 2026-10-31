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
# 网关端口 8090，探测 /admin/ 接口
for i in $(seq 1 15); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/admin/ 2>/dev/null || echo '000')
  if [ "$CODE" != "000" ]; then
    echo "[OK] 网关响应 HTTP $CODE"
    break
  fi
  echo "  等待网关启动 ($i/15)..."
  sleep 2
done

echo ""
echo "=== 服务注册检查（Nacos）==="
REGISTERED=$(docker compose -p "${APP_PROJECT}" -f "${APP_COMPOSE}" \
  ps --format "{{.Names}}" 2>/dev/null | head -20)
echo "运行中的服务:"
echo "$REGISTERED"
echo ""
echo "[提示] 如需检查 Nacos 注册详情，访问 http://192.168.20.133:8848/nacos"