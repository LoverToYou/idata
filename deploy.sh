#!/usr/bin/env bash
set -eo pipefail

# ============================================
# IDATA 部署脚本 - 构建前端 & 后端
# 用法: ./deploy.sh [dev|prod]
#   dev  - 构建前端 (vite build) + 后端 (mvn package)
#   prod - 构建前端 + 后端，并将前端产物拷贝到后端 static 目录再重新打包
# ============================================

APP_NAME="idata"
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
DEPLOY_DIR="${PROJECT_ROOT}/deploy"
MODE="${1:-dev}"

echo "========================================"
echo " IDATA 部署脚本"
echo " 模式: ${MODE}"
echo "========================================"

# ---- 1. 构建前端 ----
echo ""
echo "[1/3] 构建前端..."
cd "${FRONTEND_DIR}"

echo "  → 安装前端依赖..."
npm install --no-audit --no-fund 2>&1 | tail -3

echo "  → Vite 打包中..."
NODE_OPTIONS="--max-old-space-size=2048" ./node_modules/.bin/vite build 2>&1 || { echo "  ✗ 前端构建失败"; exit 1; }
echo "  ✓ 前端构建完成: ${FRONTEND_DIR}/dist/"

# ---- 2. 构建后端 ----
echo ""
echo "[2/3] 构建后端..."
cd "${BACKEND_DIR}"

if [ "${MODE}" = "prod" ]; then
    # 生产模式: 将前端 dist 拷贝到后端 resources/static/
    STATIC_DIR="${BACKEND_DIR}/src/main/resources/static"
    rm -rf "${STATIC_DIR}"
    cp -r "${FRONTEND_DIR}/dist" "${STATIC_DIR}"
    echo "  → 前端产物已拷贝到 ${STATIC_DIR}"
fi

echo "  → Maven 打包中..."
mvn clean package -DskipTests -q 2>&1 || { echo "  ✗ 后端构建失败"; exit 1; }
JAR_FILE=$(ls "${BACKEND_DIR}/target/${APP_NAME}-backend-"*.jar 2>/dev/null | head -1)
echo "  ✓ 后端构建完成: ${JAR_FILE}"

# ---- 3. 准备部署包 ----
echo ""
echo "[3/3] 准备部署包..."
mkdir -p "${DEPLOY_DIR}"

cp "${JAR_FILE}" "${DEPLOY_DIR}/"
cp "${PROJECT_ROOT}/start.sh" "${DEPLOY_DIR}/"

# 复制前端 dist（生产模式时不复制，已打进 jar）
if [ "${MODE}" != "prod" ]; then
    cp -r "${FRONTEND_DIR}/dist" "${DEPLOY_DIR}/frontend-dist"
fi

echo "  ✓ 部署包已就绪: ${DEPLOY_DIR}/"
echo ""
echo "部署完成！启动方式:"
echo "  开发模式: ./start.sh dev"
echo "  生产模式: ./start.sh prod"
echo ""
