# 后端镜像：多阶段（Maven 构建 → JRE 运行）。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# 端口/数据库/Redis/OSS/AI 等通过环境变量注入（见 deploy/docker-compose.yml）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
