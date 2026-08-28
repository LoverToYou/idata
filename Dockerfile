# ===========================================
# IDATA Docker 多阶段构建
# ===========================================

# ---- Stage 1: 构建前端 ----
FROM node:20-alpine AS frontend-builder
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ .
RUN npx vite build

# ---- Stage 2: 构建后端 ----
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /build
COPY backend/ ./backend/
# 将前端产物复制到后端 static 目录（生产模式）
COPY --from=frontend-builder /build/frontend/dist ./backend/src/main/resources/static
RUN mvn -f backend/pom.xml clean package -DskipTests -q

# ---- Stage 3: 运行环境 ----
# 使用 JDK：UDF 在线编译需要 jdk.compiler（javax.tools.JavaCompiler）
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# 创建非 root 用户
RUN addgroup -S idata && adduser -S idata -G idata

# 从构建阶段复制 jar 包 + UDF 编译 classpath（lib/hive 为 hive 基类、lib/runtime 为运行时依赖）
COPY --from=backend-builder /build/backend/target/idata-backend-*.jar app.jar
COPY --from=backend-builder /build/backend/lib/hive /app/lib/hive
COPY --from=backend-builder /build/backend/lib/runtime /app/lib/runtime

# DataX 配置目录（如需挂载外部 DataX）+ UDF 存储目录（生产可把 storage-path 指向 /opt/idata/udf）
RUN mkdir -p /opt/datax /opt/idata/udf && chown -R idata:idata /opt/datax /opt/idata/udf /app

USER idata

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8088/api/ || exit 1

ENTRYPOINT ["java", \
  "-jar", "app.jar" \
]
