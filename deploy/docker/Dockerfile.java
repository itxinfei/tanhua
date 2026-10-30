# ============================================================
# 通用 Java 运行时镜像（每个微服务一个独立最小构建上下文）
#
# 背景（为什么不用仓库根目录当 build context）：
#   Spring Boot 的 fat jar 每个约 70MB，11 个服务合计 ~770MB。
#   若 context 指向仓库根，每次 docker build 都要把整个仓库传给 daemon
#   （含全部 target/ 产物），11 个服务累计传输近 10GB，构建极慢甚至卡死。
#   方案：CI 先把「本服务的那一个 jar」+ 本 Dockerfile 暂存到
#         deploy/build/<服务名>/，compose 以该目录为 context，
#         每次构建只传约 70MB。暂存由 deploy/ci/stage-artifacts.sh 完成。
#
# 暂存目录内容：
#   app.jar     ← 该服务的 <svc>-1.0-SNAPSHOT.jar
#   Dockerfile  ← 本文件的副本
# ============================================================
FROM docker.m.daocloud.io/library/eclipse-temurin:25-jre-jammy

ENV JAVA_OPTS="-Xms128m -Xmx320m"

WORKDIR /app

# 暂存目录中固定命名为 app.jar（每个服务一个目录，互不干扰）
COPY app.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
