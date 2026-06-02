#!/usr/bin/env bash
# ============================================
# IDATA 构建脚本 - 提供三种构建方式的函数
# 被 deploy.sh 调用，也可单独 source 使用:
#   source build.sh && build_frontend
# ============================================

set -eo pipefail

APP_NAME="idata"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
DEPLOY_DIR="${PROJECT_ROOT}/deploy"

# ---- 环境检测 ----
check_env() {
    # 检查可用内存
    local mem_mb=0
    if command -v free &>/dev/null; then
        mem_mb=$(free -m | awk '/^Mem:/{print $7}')
    elif command -v vm_stat &>/dev/null; then
        mem_mb=$(vm_stat | awk '/free/{print $3}' | sed 's/\..*//' 2>/dev/null || echo "0")
        mem_mb=$((mem_mb / 256))
    fi

    if [ "$mem_mb" -gt 0 ] && [ "$mem_mb" -lt 1024 ]; then
        echo "  ⚠ 可用内存 ${mem_mb}MB，Vite 构建可能需要较长时间"
        echo "    若卡死可中断后使用: bash deploy.sh backend (跳过后端的前端构建)"
    fi
}

# ---- 前端构建 ----
build_frontend() {
    check_env

    echo "  → 安装前端依赖..."
    cd "${FRONTEND_DIR}"
    npm install --no-audit --no-fund 2>&1 | tail -3

    echo "  → Vite 打包中..."
    # 根据可用内存动态调整 Node 内存限制
    local max_mem=2048
    if command -v free &>/dev/null; then
        local total_mem
        total_mem=$(free -m | awk '/^Mem:/{print $2}')
        if [ "$total_mem" -gt 0 ] && [ "$total_mem" -lt 2048 ]; then
            max_mem=$((total_mem * 3 / 4))
        fi
    fi

    NODE_OPTIONS="--max-old-space-size=${max_mem}" ./node_modules/.bin/vite build 2>&1 || {
        echo "  ✗ 前端构建失败"
        echo "  提示: 可在本地构建前端后，将 dist/ 目录传到服务器，再用 bash deploy.sh backend"
        return 1
    }
    echo "  ✓ 前端构建完成: ${FRONTEND_DIR}/dist/"
}

# ---- 后端构建 ----
# $1: 是否将前端 dist 拷贝到 static (true=prod 模式)
build_backend() {
    cd "${BACKEND_DIR}"

    if [ "${1:-false}" = "true" ] && [ -d "${FRONTEND_DIR}/dist" ]; then
        local static_dir="${BACKEND_DIR}/src/main/resources/static"
        rm -rf "${static_dir}"
        cp -r "${FRONTEND_DIR}/dist" "${static_dir}"
        echo "  → 前端产物已合并到后端 static/"
    fi

    echo "  → Maven 打包中..."
    mvn clean package -DskipTests -q 2>&1 || {
        echo "  ✗ 后端构建失败" >&2
        return 1
    }
    local jar
    jar=$(ls "${BACKEND_DIR}/target/${APP_NAME}-backend-"*.jar 2>/dev/null | head -1)
    echo "${jar}"
}

# ---- 打包部署 ----
# $1: jar 文件路径
package() {
    local jar_file="$1"
    if [ -z "${jar_file}" ] || [ ! -f "${jar_file}" ]; then
        echo "  ✗ 未找到 jar 包"
        return 1
    fi

    mkdir -p "${DEPLOY_DIR}"
    cp "${jar_file}" "${DEPLOY_DIR}/"
    cp "${PROJECT_ROOT}/start.sh" "${DEPLOY_DIR}/"

    if [ -d "${FRONTEND_DIR}/dist" ]; then
        cp -r "${FRONTEND_DIR}/dist" "${DEPLOY_DIR}/frontend-dist"
    fi

    echo "  ✓ 部署包已就绪: ${DEPLOY_DIR}/"
    echo "    启动方式: ./start.sh dev 或 ./start.sh prod"
}

# ---- 三种构建模式 ----

# 1. 快速模式：仅打包后端，复用已有前端 dist
build_backend_only() {
    echo "  快速模式：跳过前端构建"
    if [ ! -d "${FRONTEND_DIR}/dist" ]; then
        build_frontend || return 1
    else
        echo "  ✓ 复用已有前端 dist"
    fi

    local jar
    jar=$(build_backend false | tail -1 | xargs) || return 1
    if [ -z "${jar}" ] || [ ! -f "${jar}" ]; then
        echo "  ✗ 未找到构建产物 jar" >&2
        return 1
    fi
    package "${jar}"
}

# 2. 开发模式：构建前端 + 后端
build_dev() {
    build_frontend || return 1
    local jar
    jar=$(build_backend false | tail -1 | xargs) || return 1
    if [ -z "${jar}" ] || [ ! -f "${jar}" ]; then
        echo "  ✗ 未找到构建产物 jar" >&2
        return 1
    fi
    package "${jar}"
}

# 3. 生产模式：构建前端（合并到后端 jar）+ 后端
build_prod() {
    build_frontend || return 1
    local jar
    jar=$(build_backend true | tail -1 | xargs) || return 1
    if [ -z "${jar}" ] || [ ! -f "${jar}" ]; then
        echo "  ✗ 未找到构建产物 jar" >&2
        return 1
    fi
    package "${jar}"
}

# ---- 直接执行 ----
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "========================================"
    echo " IDATA 构建脚本"
    echo " 用法: source build.sh 调用函数"
    echo "========================================"
fi
