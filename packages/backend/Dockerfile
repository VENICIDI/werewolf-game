# ============================================================
# 狼人杀 backend Dockerfile（多阶段构建，用于微信云托管）
# 阶段1: maven 构建 jar；阶段2: JRE + jar 轻量运行
# ============================================================

# ---------- 阶段 1: 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先拷 pom 利用 docker 层缓存（依赖不变则复用）
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./mvnw
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline || true

# 再拷源码
COPY src ./src

# 打包（跳测试节省构建时间，云托管里重跑无意义）
RUN ./mvnw -B clean package -DskipTests \
 && mv target/*.jar /build/app.jar

# ---------- 阶段 2: 运行 ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# 时区 + 常用字体（避免中文日志乱码）
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS=""

# 复制构建产物
COPY --from=builder /build/app.jar /app/app.jar

# 业务默认端口（云托管会自动映射对外）
EXPOSE 8088

# 云托管传入的环境变量示例（在平台控制台配置）：
#   SERVER_PORT=8088
#   MYSQL_HOST=xxx.tencentcdb.com
#   MYSQL_PORT=3306
#   MYSQL_USERNAME=xxx
#   MYSQL_PASSWORD=xxx
#   MYSQL_DATABASE=werewolf
#   REDIS_HOST=xxx.redis.tencentcloud.com
#   REDIS_PORT=6379
#   REDIS_PASSWORD=xxx
#   AI_SERVICE_URL=http://werewolf-ai           # 云托管同环境服务名直连
#   WECHAT_APPID=wxb0e543c0df784e35
#   WECHAT_SECRET=xxx
#   JWT_SECRET=xxx

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
