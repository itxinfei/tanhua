# ============================================================
# 通用 Java 运行时镜像（Gitea Actions 构建 11 个 Spring Boot 服务容器用）
#
# 构建时由 deploy/docker-compose.app.yml 通过 args.SERVICE=<模块名> 传入。
# jar 已在 CI 的 maven package 阶段生成：<SERVICE>/target/<SERVICE>-1.0-SNAPSHOT.jar
# 服务用 network_mode: host 运行，故连接串里的 127.0.0.1 直接命中宿主机上已映射的中间件端口。
# ============================================================
FROM eclipse-temurin:25-jre-jammy

ARG SERVICE
ENV JAVA_OPTS="-Xms128m -Xmx320m"

WORKDIR /app

COPY ${SERVICE}/target/${SERVICE}-1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
