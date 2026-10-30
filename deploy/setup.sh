#!/usr/bin/env bash
# ============================================================
# 心飞交友中间件一键部署脚本
# 在 deploy/ 目录下执行：bash setup.sh
# 依赖：Docker + docker compose v2
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE_FILE="docker-compose.middleware.yml"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
SQL_FILE="../scripts/mytanhua.sql"

log()  { echo -e "\033[32m[setup]\033[0m $*"; }
warn() { echo -e "\033[33m[warn]\033[0m $*" >&2; }
fail() { echo -e "\033[31m[fail]\033[0m $*" >&2; exit 1; }

command -v docker >/dev/null || fail "未安装 Docker"
docker compose version >/dev/null 2>&1 || fail "未安装 docker compose v2（需 v2.x）"

# 1. ES 前置内核参数
MAX_MAP=$(cat /proc/sys/vm/max_map_count 2>/dev/null || echo 0)
if [ "$MAX_MAP" -lt 262144 ]; then
  log "设置 vm.max_map_count=262144（ES 启动必需）"
  sudo sysctl -w vm.max_map_count=262144 >/dev/null
  grep -q 'vm.max_map_count' /etc/sysctl.conf 2>/dev/null || \
    echo 'vm.max_map_count = 262144' | sudo tee -a /etc/sysctl.conf >/dev/null
fi

# 2. 启动全部容器
log "启动中间件容器（首次拉镜像较慢）..."
docker compose -f "$COMPOSE_FILE" up -d

# 2.1 修复 named volume 权限（broker/nacos 容器内非 root 用户无写权限会导致启动 NPE/255）
log "修复数据卷权限..."
docker run --rm --user root -v xfjy-middleware_rocketmq-broker-store:/home/rocketmq/store \
  apache/rocketmq:5.3.1 chown -R rocketmq:rocketmq /home/rocketmq/store 2>/dev/null && \
  docker restart xfjy-rocketmq-broker >/dev/null 2>&1 && log "broker store 权限已修复" || warn "broker store chown 跳过（可能已正确）"
docker run --rm --user root -v xfjy-middleware_nacos-data:/home/nacos/data \
  nacos/nacos-server:v3.0.0 chown -R 1000:1000 /home/nacos/data 2>/dev/null && \
  docker restart xfjy-nacos >/dev/null 2>&1 && log "nacos data 权限已修复" || warn "nacos data chown 跳过（可能已正确）"

# 3. 等待 MySQL 就绪
log "等待 MySQL 就绪..."
for i in $(seq 1 60); do
  if docker exec xfjy-mysql mysqladmin ping -h127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent 2>/dev/null; then
    break
  fi
  sleep 2
  [ "$i" -eq 60 ] && fail "MySQL 120s 内未就绪，查看日志：docker logs xfjy-mysql"
done

# 4. 导入全量 SQL（已有表则跳过）
if [ -f "$SQL_FILE" ]; then
  TABLE_COUNT=$(docker exec xfjy-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='xfjy'" 2>/dev/null || echo 0)
  if [ "$TABLE_COUNT" -gt 0 ]; then
    warn "xfjy 库已有 ${TABLE_COUNT} 张表，跳过导入（如需重置请先清库）"
  else
    log "导入全量 SQL：$SQL_FILE"
    docker exec -i xfjy-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
      --default-character-set=utf8mb4 xfjy < "$SQL_FILE"
    log "SQL 导入完成"
  fi
else
  warn "未找到 $SQL_FILE（被 .gitignore 忽略需单独上传），请手动导入："
  warn "  docker exec -i xfjy-mysql mysql -uroot -p$MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 xfjy < scripts/mytanhua.sql"
fi

# 5. 等待容器稳定
log "等待容器健康（约 30s）..."
sleep 30
docker compose -f "$COMPOSE_FILE" ps

# 6. 逐项探活
log "逐项健康检查："
check() {
  local name="$1" cmd="$2"
  if eval "$cmd" >/dev/null 2>&1; then
    echo -e "  \033[32m✓\033[0m $name"
  else
    echo -e "  \033[31m✗\033[0m $name"
  fi
}
check "MySQL 3306"    "docker exec xfjy-mysql mysqladmin ping -h127.0.0.1 -uroot -p$MYSQL_ROOT_PASSWORD --silent"
check "Redis 6379"    "docker exec xfjy-redis redis-cli ping"
check "MongoDB 27017" "docker exec xfjy-mongo mongosh --quiet --eval 'db.adminCommand(\"ping\").ok'"
check "ES 9200"       "curl -sf http://127.0.0.1:9200"
check "Nacos 8848"    "curl -sf -o /dev/null http://127.0.0.1:8848/nacos"
check "RocketMQ 9876" "docker exec xfjy-rocketmq-broker sh mqadmin clusterList -n rocketmq-namesrv:9876"
check "Qdrant 6333"   "curl -sf http://127.0.0.1:6333/healthz"
check "Neo4j 7474"    "curl -sf http://127.0.0.1:7474"

# 7. 数据库表数
TABLE_COUNT=$(docker exec xfjy-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='xfjy'" 2>/dev/null || echo 0)
log "xfjy 库表数量：$TABLE_COUNT（预期 15）"

log "部署完成（8 件中间件）。"
log "CI/CD（Gitea）不在本编排，需单独启动：docker compose -f docker-compose.gitea.yml --env-file gitea/.env up -d，详见部署文档 10.6"
log "Nacos 控制台：http://192.168.20.133:8848/nacos"
log "Neo4j Browser：http://192.168.20.133:7474"