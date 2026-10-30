#!/bin/bash
# ============================================================
# 心飞交友 - 后端服务部署脚本（CI/CD 部署阶段调用）
#
# 用法（在宿主机上下文执行）:
#   bash deploy-service.sh <service-name> [jar路径]
#
# 例:
#   bash deploy-service.sh xfjy-server
#   bash deploy-service.sh xfjy-video-service
#
# 动作:
#   1. 校验 jar 存在
#   2. 备份当前 jar（保留带时间戳的 .bak）
#   3. 停止该服务的旧 java 进程
#   4. 替换为新 jar
#   5. 用 setsid 彻底脱离会话重启（避免 SSH 断开被连带杀死）
#   6. 健康检查：等待进程起来 + 日志出现 Started 标志
#
# 退出码:
#   0 = 部署成功   1 = 部署失败
# ============================================================
set -u

SERVICE="${1:-}"
JAR_SRC="${2:-}"
BASE_DIR="${XFJY_BASE_DIR:-/home/itxinfei/mytanhua}"
LOG_DIR="${XFJY_LOG_DIR:-/home/itxinfei/logs}"
JDK_HOME="${XFJY_JDK_HOME:-/home/itxinfei/tools/jdk25}"

if [ -z "$SERVICE" ]; then
  echo "[ERROR] 未指定服务名。用法: bash deploy-service.sh <service-name> [jar路径]"
  exit 1
fi

TARGET_JAR="${BASE_DIR}/${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar"

# 未指定源 jar 时，默认取当前工作目录下的构建产物
if [ -z "$JAR_SRC" ]; then
  JAR_SRC="$(pwd)/${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar"
fi

echo "[$(date '+%F %T')] ===== 部署 ${SERVICE} ====="

# ---------- 1) 校验 jar ----------
if [ ! -f "$JAR_SRC" ]; then
  echo "[ERROR] 源 jar 不存在: ${JAR_SRC}"
  exit 1
fi
if [ ! -f "$TARGET_JAR" ]; then
  echo "[WARN] 目标 jar 不存在(将首次部署): ${TARGET_JAR}"
fi
echo "[INFO] 源 jar   : ${JAR_SRC} ($(stat -c%s "$JAR_SRC") bytes)"
echo "[INFO] 目标 jar : ${TARGET_JAR}"

# ---------- 2) 备份旧 jar ----------
if [ -f "$TARGET_JAR" ]; then
  BAK="${TARGET_JAR}.bak.$(date +%Y%m%d%H%M%S)"
  cp -f "$TARGET_JAR" "$BAK"
  echo "[INFO] 已备份旧 jar: ${BAK}"
fi

# ---------- 3) 停止旧进程 ----------
OLD_PIDS=$(pgrep -f "${SERVICE}-1.0-SNAPSHOT.jar" | tr '\n' ' ' || true)
if [ -n "${OLD_PIDS// /}" ]; then
  echo "[INFO] 停止旧进程: ${OLD_PIDS}"
  kill -9 ${OLD_PIDS} 2>/dev/null || true
  sleep 3
else
  echo "[INFO] 无旧进程（首次部署）"
fi

# ---------- 4) 替换 jar ----------
mkdir -p "$(dirname "$TARGET_JAR")"
if [ "$(readlink -f "$JAR_SRC")" = "$(readlink -f "$TARGET_JAR")" ]; then
  echo "[INFO] 源与目标是同一文件，跳过拷贝（原地部署）"
else
  cp -f "$JAR_SRC" "$TARGET_JAR"
  echo "[INFO] jar 替换完成"
fi

# ---------- 5) 重启（setsid 脱离会话） ----------
mkdir -p "$LOG_DIR"
LOG_FILE="${LOG_DIR}/${SERVICE}.log"

export JAVA_HOME="$JDK_HOME"
export PATH="${JDK_HOME}/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
export MYSQL_URL="${XFJY_MYSQL_URL:-jdbc:mysql://127.0.0.1:3306/xfjy?useUnicode=true&characterEncoding=utf8&autoReconnect=true&allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
export MYSQL_USERNAME="${XFJY_MYSQL_USERNAME:-xfjy}"
export MYSQL_PASSWORD="${XFJY_MYSQL_PASSWORD:-123456}"
export REDIS_HOST="${XFJY_REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${XFJY_REDIS_PORT:-6379}"
export REDIS_PASSWORD="${XFJY_REDIS_PASSWORD:-}"
export NACOS_SERVER_ADDR="${XFJY_NACOS_ADDR:-127.0.0.1:8848}"
export NACOS_PASSWORD="${XFJY_NACOS_PASSWORD:-}"
export ROCKETMQ_NAME_SERVER="${XFJY_ROCKETMQ:-127.0.0.1:9876}"
export NEO4J_PASSWORD="${XFJY_NEO4J_PASSWORD:-itxinfei123456}"

cd "$BASE_DIR" || exit 1
setsid nohup java -Xms128m -Xmx256m -jar "$TARGET_JAR" > "$LOG_FILE" 2>&1 < /dev/null &
echo "[INFO] 已下发启动命令，日志: ${LOG_FILE}"

# ---------- 6) 健康检查 ----------
echo "[INFO] 等待服务启动 (最多 90s)..."
for i in $(seq 1 90); do
  if pgrep -f "${SERVICE}-1.0-SNAPSHOT.jar" > /dev/null 2>&1 \
     && grep -aq "Started .*Application in" "$LOG_FILE" 2>/dev/null; then
    echo "[OK] ${SERVICE} 启动成功 (耗时约 ${i}s)"
    grep -a "Started .*Application in" "$LOG_FILE" | tail -1
    exit 0
  fi
  # 进程挂了就不再等了
  if ! pgrep -f "${SERVICE}-1.0-SNAPSHOT.jar" > /dev/null 2>&1 && [ "$i" -gt 10 ]; then
    echo "[ERROR] ${SERVICE} 进程已退出，日志尾部:"
    tail -n 30 "$LOG_FILE" 2>/dev/null
    exit 1
  fi
  sleep 1
done

echo "[ERROR] ${SERVICE} 启动超时(90s)，日志尾部:"
tail -n 30 "$LOG_FILE" 2>/dev/null
exit 1
