#!/usr/bin/env bash
set -euo pipefail

# ============================================
# IDATA 启动脚本
# 用法:
#   ./start.sh dev          - 开发模式 (后端 jar + 前端 dev server)
#   ./start.sh prod         - 生产模式 (仅后端 jar，前端由 nginx 等托管)
#   ./start.sh backend      - 仅启动后端
#   ./start.sh frontend     - 仅启动前端开发服务器
#   ./start.sh stop         - 停止后端服务
#   ./start.sh status       - 查看服务状态
# ============================================

APP_NAME="idata"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 定位项目根目录：从当前目录或上级目录定位 backend/frontend
if [ -d "${SCRIPT_DIR}/backend" ]; then
    PROJECT_ROOT="${SCRIPT_DIR}"
elif [ -d "$(dirname "${SCRIPT_DIR}")/backend" ]; then
    PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
else
    PROJECT_ROOT="${SCRIPT_DIR}"
fi

BACKEND_DIR="${PROJECT_ROOT}/backend"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
DEPLOY_DIR="${PROJECT_ROOT}/deploy"
LOG_DIR="${PROJECT_ROOT}/logs"
PID_FILE="${PROJECT_ROOT}/.${APP_NAME}.pid"

# 查找最新构建的 jar（优先 deploy/，其次 target/）
find_jar() {
    local jar
    jar=$(ls "${DEPLOY_DIR}/${APP_NAME}-backend-"*.jar 2>/dev/null | head -1)
    if [ -z "${jar}" ]; then
        jar=$(ls "${BACKEND_DIR}/target/${APP_NAME}-backend-"*.jar 2>/dev/null | head -1)
    fi
    echo "${jar}"
}

# 确保日志目录存在
mkdir -p "${LOG_DIR}"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ---- 后端启动/停止 ----
start_backend() {
    local jar_path
    jar_path=$(find_jar)

    if [ -z "${jar_path}" ]; then
        error "未找到后端 jar 包！请先执行 ./deploy.sh"
        info "尝试直接使用 mvn spring-boot:run..."
        cd "${BACKEND_DIR}"
        mvn spring-boot:run -q > "${LOG_DIR}/backend.log" 2>&1 &
        local pid=$!
        echo "${pid}" > "${PID_FILE}"
        info "后端启动中 (PID: ${pid})，日志: logs/backend.log"
        return
    fi

    info "使用 jar: ${jar_path}"
    nohup java -jar "${jar_path}" \
        > "${LOG_DIR}/backend.log" 2>&1 &
    local pid=$!
    echo "${pid}" > "${PID_FILE}"
    info "后端启动中 (PID: ${pid})，日志: logs/backend.log"

    # 等待后端就绪
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8088/api/ > /dev/null 2>&1; then
            info "后端已就绪 (HTTP :8088)"
            return 0
        fi
        sleep 1
    done
    warn "后端启动超时，请检查日志: logs/backend.log"
}

stop_backend() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
        if kill -0 "${PID}" 2>/dev/null; then
            info "停止后端服务 (PID: ${PID})..."
            kill "${PID}" 2>/dev/null || true
            sleep 2
            if kill -0 "${PID}" 2>/dev/null; then
                kill -9 "${PID}" 2>/dev/null || true
                info "已强制停止后端服务"
            else
                info "后端服务已停止"
            fi
        else
            warn "PID ${PID} 不存在，清理 stale pid 文件"
        fi
        rm -f "${PID_FILE}"
    else
        # 尝试从端口查找
        PID=$(lsof -ti tcp:8088 2>/dev/null || true)
        if [ -n "${PID}" ]; then
            info "停止端口 8088 上的进程 (PID: ${PID})..."
            kill "${PID}" 2>/dev/null || true
        else
            info "后端服务未运行"
        fi
    fi
}

status_backend() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
        if kill -0 "${PID}" 2>/dev/null; then
            info "后端服务运行中 (PID: ${PID})"
            if curl -sf http://localhost:8088/api/ > /dev/null 2>&1; then
                info "后端 HTTP 响应正常 (:8088)"
            else
                warn "后端 PID 存在但 HTTP 无响应"
            fi
        else
            warn "后端 PID 文件存在但进程已不存在"
            rm -f "${PID_FILE}"
        fi
    else
        PID=$(lsof -ti tcp:8088 2>/dev/null || true)
        if [ -n "${PID}" ]; then
            info "后端服务运行中 (PID: ${PID})，但无 pid 文件"
        else
            info "后端服务未运行"
        fi
    fi
}

# ---- 前端启动 ----
start_frontend_dev() {
    if [ ! -d "${FRONTEND_DIR}/node_modules" ]; then
        info "安装前端依赖..."
        cd "${FRONTEND_DIR}"
        npm install
    fi
    info "启动前端开发服务器 (:5173)..."
    cd "${FRONTEND_DIR}"
    npm run dev
}

# ---- 主逻辑 ----
case "${1:-dev}" in
    dev)
        info "启动 IDATA 开发模式..."
        start_backend
        # 前端在前台运行，Ctrl+C 即可停止
        info "启动前端开发服务器..."
        start_frontend_dev
        ;;
    prod)
        info "启动 IDATA 生产模式..."
        start_backend
        info "生产模式下前端请使用 Nginx 等 Web 服务器托管: ${FRONTEND_DIR}/dist/"
        info "Nginx 配置示例:"
        echo '  server {'
        echo '      listen 80;'
        echo '      server_name your-domain;'
        echo '      root /path/to/frontend/dist;'
        echo '      location /api/ { proxy_pass http://localhost:8088; }'
        echo '  }'
        ;;
    backend)
        start_backend
        ;;
    frontend)
        start_frontend_dev
        ;;
    stop)
        stop_backend
        ;;
    status)
        status_backend
        ;;
    restart)
        stop_backend
        sleep 1
        start_backend
        ;;
    *)
        echo "用法: $0 {dev|prod|backend|frontend|stop|status|restart}"
        exit 1
        ;;
esac
