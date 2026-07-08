# syntax=docker/dockerfile:1.7

# 后端镜像：多阶段（Maven 构建 → JRE 运行）。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先预取依赖，再拷贝源码，避免每次源码变更都重新拉取 Maven 依赖。
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# 端口/数据库/Redis/OSS/AI 等通过环境变量注入（见 deploy/docker-compose.yml）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
