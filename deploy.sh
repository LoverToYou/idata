#!/usr/bin/env bash
# ============================================
# IDATA 部署脚本 - 三种构建方式
# 用法:
#   ./deploy.sh           完整构建: 前端 + 后端
#   ./deploy.sh backend   快速模式: 仅打包后端 (复用已有前端)
#   ./deploy.sh prod      生产模式: 前端打包进后端 jar
# ============================================

set -eo pipefail

cd "$(dirname "$0")"

# 加载构建函数
source ./build.sh

MODE="${1:-dev}"

echo "========================================"
echo " IDATA 部署脚本"
echo " 模式: ${MODE}"
echo "========================================"

case "${MODE}" in
    backend)
        build_backend_only
        ;;
    prod)
        build_prod
        ;;
    dev|full|*)
        build_dev
        ;;
esac
