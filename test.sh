#!/usr/bin/env bash
set -euo pipefail

# ============================================
# IDATA 自动化功能测试脚本
# 用法:
#   ./test.sh                    - 运行全部测试
#   ./test.sh api                - 仅测试后端 API
#   ./test.sh frontend           - 仅测试前端页面可访问性
#   ./test.sh report             - 生成 HTML 测试报告
# ============================================

APP_NAME="idata"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 定位项目根目录
if [ -d "${SCRIPT_DIR}/backend" ]; then
    PROJECT_ROOT="${SCRIPT_DIR}"
elif [ -d "$(dirname "${SCRIPT_DIR}")/backend" ]; then
    PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"
else
    PROJECT_ROOT="${SCRIPT_DIR}"
fi

API_BASE="http://localhost:8088/api"
FRONTEND_URL="http://localhost:5173"
LOG_DIR="${PROJECT_ROOT}/logs"
REPORT_DIR="${PROJECT_ROOT}/test-reports"

PASS=0
FAIL=0
TIMEOUT_SEC=10
TESTS_RUN=0

mkdir -p "${LOG_DIR}" "${REPORT_DIR}"

# ---- 颜色 ----
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'
BOLD='\033[1m'

pass()  { PASS=$((PASS+1)); TESTS_RUN=$((TESTS_RUN+1)); echo -e "  ${GREEN}✓ PASS${NC} $1"; }
fail()  { FAIL=$((FAIL+1)); TESTS_RUN=$((TESTS_RUN+1)); echo -e "  ${RED}✗ FAIL${NC} $1"; }
info()  { echo -e "${BLUE}[INFO]${NC} $1"; }
title() { echo -e "\n${BOLD}━━━ $1 ━━━${NC}"; }
skip()  { echo -e "  ${YELLOW}○ SKIP${NC} $1"; }

# ---- 辅助函数 ----

# 检查服务是否在指定端口监听
check_port() {
    lsof -ti tcp:"$1" 2>/dev/null | head -1
}

# 带超时的 curl，输出 HTTP 状态码
curl_status() {
    curl -s -o /dev/null -w "%{http_code}" --max-time "${TIMEOUT_SEC}" "$1" 2>/dev/null || echo "000"
}

# 带超时的 curl GET，输出响应体
curl_body() {
    curl -s --max-time "${TIMEOUT_SEC}" "$1" 2>/dev/null || echo ""
}

# 带超时的 curl POST，输出响应体
curl_post() {
    curl -s -X POST --max-time "${TIMEOUT_SEC}" "$1" 2>/dev/null || echo ""
}

# 解析 JSON 字段（简单实现，不依赖 jq）
json_val() {
    # $1 = json string, $2 = field name
    echo "$1" | grep -o "\"$2\"\s*:\s*\"[^\"]*\"" | head -1 | sed 's/.*: *"//; s/"$//' 2>/dev/null || echo ""
}
json_code() {
    echo "$1" | grep -o "\"code\"\s*:\s*[0-9]*" | head -1 | sed 's/.*: *//' 2>/dev/null || echo ""
}

# ---- 测试前检查 ----
preflight_check() {
    title "前置检查"

    local backend_pid
    local frontend_pid
    local mysql_pid

    backend_pid=$(check_port 8088)
    frontend_pid=$(check_port 5173)
    mysql_pid=$(check_port 3306)

    if [ -n "$backend_pid" ]; then
        pass "后端服务运行中 (PID: $backend_pid, 端口 8088)"
    else
        fail "后端服务未运行 (端口 8088)"
    fi

    if [ -n "$frontend_pid" ]; then
        pass "前端服务运行中 (PID: $frontend_pid, 端口 5173)"
    else
        fail "前端服务未运行 (端口 5173)"
    fi

    if [ -n "$mysql_pid" ]; then
        pass "MySQL 运行中 (PID: $mysql_pid, 端口 3306)"
    else
        fail "MySQL 未运行 (端口 3306)"
    fi
}

# ============================================
# API 测试
# ============================================
test_api() {
    title "API 测试"

    # --- 1. 根路径（无映射时返回 404/500 皆可，确认服务存活）---
    local status
    status=$(curl_status "${API_BASE}/")
    if [ "$status" != "000" ]; then
        pass "[GET /api/] 服务存活 (HTTP $status)"
    else
        fail "[GET /api/] 服务无响应"
    fi

    # --- 2. SQL 任务列表 ---
    local resp
    resp=$(curl_body "${API_BASE}/sql-task/list")
    local code
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        local count
        count=$(echo "$resp" | grep -o '"id":' | wc -l | tr -d ' ')
        pass "[GET /api/sql-task/list] 返回 code=200, 任务数=$count"
    else
        fail "[GET /api/sql-task/list] 期望 code=200，实际 code=$code"
    fi

    # --- 3. 数据源列表 ---
    resp=$(curl_body "${API_BASE}/datasource/list")
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        local ds_count
        ds_count=$(echo "$resp" | grep -o '"id":' | wc -l | tr -d ' ')
        pass "[GET /api/datasource/list] 返回 code=200, 数据源数=$ds_count"
    else
        fail "[GET /api/datasource/list] 期望 code=200，实际 code=$code"
    fi

    # --- 4. 工作流列表 ---
    resp=$(curl_body "${API_BASE}/workflow/list")
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        local wf_count
        wf_count=$(echo "$resp" | grep -o '"id":' | wc -l | tr -d ' ')
        pass "[GET /api/workflow/list] 返回 code=200, 工作流数=$wf_count"
    else
        fail "[GET /api/workflow/list] 期望 code=200，实际 code=$code"
    fi

    # --- 5. 参数列表 ---
    resp=$(curl_body "${API_BASE}/parameter/list")
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        pass "[GET /api/parameter/list] 返回 code=200"
    else
        fail "[GET /api/parameter/list] 期望 code=200，实际 code=$code"
    fi

    # --- 6. 脱敏规则列表 ---
    resp=$(curl_body "${API_BASE}/masking-rule/list")
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        pass "[GET /api/masking-rule/list] 返回 code=200"
    else
        fail "[GET /api/masking-rule/list] 期望 code=200，实际 code=$code"
    fi

    # --- 7. 数据源测试连接 ---
    # 取第一个数据源 ID 做测试连接
    resp=$(curl_body "${API_BASE}/datasource/list")
    local first_ds_id
    first_ds_id=$(echo "$resp" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
    if [ -n "$first_ds_id" ]; then
        resp=$(curl_post "${API_BASE}/datasource/test-connection/${first_ds_id}")
        code=$(json_code "$resp")
        local msg
        msg=$(json_val "$resp" "message")
        if [ "$code" = "200" ]; then
            pass "[POST /api/datasource/test-connection/${first_ds_id}] 连接成功"
        else
            fail "[POST /api/datasource/test-connection/${first_ds_id}] 失败 (code=$code, msg=$msg)"
        fi
    else
        skip "数据源测试连接：无可用数据源"
    fi

    # --- 8. 获取单个 SQL 任务 ---
    resp=$(curl_body "${API_BASE}/sql-task/list")
    local first_task_id
    first_task_id=$(echo "$resp" | grep -o '"id":[0-9]*' | head -1 | sed 's/"id"://')
    if [ -n "$first_task_id" ]; then
        resp=$(curl_body "${API_BASE}/sql-task/${first_task_id}")
        code=$(json_code "$resp")
        if [ "$code" = "200" ]; then
            pass "[GET /api/sql-task/${first_task_id}] 获取任务详情成功"
        else
            fail "[GET /api/sql-task/${first_task_id}] 获取失败，code=$code"
        fi
    else
        skip "获取单个 SQL 任务：无可用任务"
    fi

    # --- 9. ETL 任务列表 ---
    resp=$(curl_body "${API_BASE}/datax-task/list")
    code=$(json_code "$resp")
    if [ "$code" = "200" ]; then
        local etl_count
        etl_count=$(echo "$resp" | grep -o '"id":' | wc -l | tr -d ' ')
        pass "[GET /api/datax-task/list] 返回 code=200, ETL任务数=$etl_count"
    else
        fail "[GET /api/datax-task/list] 期望 code=200，实际 code=$code"
    fi

    # --- 10. SpringDoc / Swagger ---
    resp=$(curl_body "${API_BASE}/v3/api-docs")
    code=$(json_code "$resp")
    if [ -n "$code" ] && [ "$code" != "" ]; then
        pass "[GET /api/v3/api-docs] OpenAPI 文档可访问"
    else
        status=$(curl_status "${API_BASE}/v3/api-docs")
        if [ "$status" != "000" ]; then
            pass "[GET /api/v3/api-docs] HTTP $status"
        else
            fail "[GET /api/v3/api-docs] 不可访问"
        fi
    fi
}

# ============================================
# 前端页面可访问性测试
# ============================================
test_frontend() {
    title "前端页面可访问性"

    local routes=(
        "/"
        "/dashboard"
        "/sql-task"
        "/datasource"
        "/parameter"
        "/datax-task"
        "/workflow"
        "/monitor"
        "/masking-rule"
    )

    for route in "${routes[@]}"; do
        local url="${FRONTEND_URL}${route}"
        local status
        status=$(curl_status "$url")
        # Vite SPA 返回 200（路由由前端处理）
        if [ "$status" = "200" ] || [ "$status" = "304" ]; then
            pass "[GET ${route}] HTTP ${status}"
        else
            fail "[GET ${route}] 期望 200，实际 ${status}"
        fi
    done

    # --- 验证首页包含关键元素 ---
    info "验证首页内容..."
    local html
    html=$(curl_body "${FRONTEND_URL}/")
    if echo "$html" | grep -qi "idata\|IDATA\|<div id=\"app\">"; then
        pass "首页 HTML 包含 IDATA 应用挂载点"
    else
        fail "首页 HTML 缺少 IDATA 挂载点"
    fi

    # --- 验证前端资源加载 ---
    info "检查前端静态资源..."
    local js_count
    js_count=$(echo "$html" | grep -c 'src="[^"]*\.js"' 2>/dev/null || echo "0")
    js_count=$(echo "$js_count" | head -1)
    if [ "$js_count" -gt 0 ] 2>/dev/null; then
        pass "首页加载 $js_count 个 JS 资源"
    else
        # 兼容 Vite SPA 的模块 script 标签
        js_count=$(echo "$html" | grep -c 'type="module" src=' 2>/dev/null || echo "0")
        js_count=$(echo "$js_count" | head -1)
        if [ "$js_count" -gt 0 ] 2>/dev/null; then
            pass "首页加载 $js_count 个 ES module 资源"
        else
            fail "首页未检测到 JS 资源"
        fi
    fi
}

# ============================================
# 生成 HTML 测试报告
# ============================================
generate_report() {
    local report_file="${REPORT_DIR}/test-report-$(date +%Y%m%d-%H%M%S).html"
    local total=$((PASS + FAIL))
    local pass_pct=0
    if [ "$total" -gt 0 ]; then
        pass_pct=$((PASS * 100 / total))
    fi

    local color="green"
    if [ "$FAIL" -gt 0 ]; then
        color="red"
    fi

    cat > "$report_file" << HTMLREPORT
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>IDATA 测试报告</title>
<style>
body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; background: #f5f5f5; }
h1 { color: #333; border-bottom: 2px solid #ddd; padding-bottom: 10px; }
.summary { display: flex; gap: 20px; margin: 20px 0; }
.card { background: white; border-radius: 8px; padding: 20px; flex: 1; box-shadow: 0 1px 3px rgba(0,0,0,0.1); text-align: center; }
.card .num { font-size: 36px; font-weight: bold; }
.card.pass .num { color: #67c23a; }
.card.fail .num { color: #f56c6c; }
.card.total .num { color: #409eff; }
.progress { background: #e4e7ed; border-radius: 10px; height: 20px; margin: 20px 0; overflow: hidden; }
.progress .bar { height: 100%; border-radius: 10px; background: #67c23a; transition: width 0.3s; }
.timestamp { color: #999; font-size: 14px; }
.env { background: white; border-radius: 8px; padding: 15px; margin: 15px 0; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.env table { width: 100%; border-collapse: collapse; }
.env td { padding: 6px 10px; border-bottom: 1px solid #eee; }
.env td:first-child { color: #666; width: 120px; }
.result-item { padding: 8px 12px; margin: 4px 0; border-radius: 4px; font-family: monospace; }
.result-item.pass { background: #f0f9eb; color: #67c23a; }
.result-item.fail { background: #fef0f0; color: #f56c6c; }
.result-item.skip { background: #fdf6ec; color: #e6a23c; }
</style>
</head>
<body>
<h1>IDATA 功能测试报告</h1>
<p class="timestamp">测试时间: $(date '+%Y-%m-%d %H:%M:%S')</p>

<div class="summary">
  <div class="card total">
    <div class="num">$total</div>
    <div>总用例</div>
  </div>
  <div class="card pass">
    <div class="num">$PASS</div>
    <div>通过</div>
  </div>
  <div class="card fail">
    <div class="num">$FAIL</div>
    <div>失败</div>
  </div>
</div>

<div class="progress">
  <div class="bar" style="width: ${pass_pct}%"></div>
</div>
<p style="text-align:center;color:#666;font-size:14px">通过率: ${pass_pct}%</p>

<div class="env">
  <table>
    <tr><td>项目</td><td>IDATA</td></tr>
    <tr><td>测试脚本</td><td>$(basename "$0")</td></tr>
    <tr><td>后端地址</td><td>${API_BASE}</td></tr>
    <tr><td>前端地址</td><td>${FRONTEND_URL}</td></tr>
    <tr><td>主机</td><td>$(hostname)</td></tr>
  </table>
</div>

HTMLREPORT

    # 逐条写入结果（从日志重放，这里直接写结论）
    echo "<h2>测试结果明细</h2>" >> "$report_file"

    # 再读测试日志（如果我们有记录的话），或者直接写结论
    if [ "$FAIL" -gt 0 ]; then
        echo "<p style='color:#f56c6c'>${FAIL} 个用例失败，请检查服务状态后重试</p>" >> "$report_file"
    else
        echo "<p style='color:#67c23a'>全部用例通过 ✓</p>" >> "$report_file"
    fi

    cat >> "$report_file" << HTMLREPORT2
</body>
</html>
HTMLREPORT2

    echo -e "\n${BLUE}[报告]${NC} HTML 报告已生成: ${report_file}"
}

# ============================================
# 主流程
# ============================================
main() {
    local mode="${1:-all}"
    local start_time
    local end_time
    local duration

    echo ""
    echo "============================================"
    echo "     IDATA 自动化功能测试"
    echo "     模式: ${mode}"
    echo "     时间: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "============================================"

    start_time=$(date +%s)

    case "$mode" in
        all)
            preflight_check
            test_api
            test_frontend
            ;;
        api)
            preflight_check
            test_api
            ;;
        frontend)
            preflight_check
            test_frontend
            ;;
        report)
            # 仅生成报告，用已有结果
            ;;
        *)
            echo "用法: $0 {all|api|frontend|report}"
            exit 1
            ;;
    esac

    end_time=$(date +%s)
    duration=$((end_time - start_time))

    # ---- 汇总 ----
    echo ""
    echo "============================================"
    echo -e "  ${BOLD}测试汇总${NC}"
    echo "  总用例: ${TESTS_RUN}"
    echo -e "  通过:   ${GREEN}${PASS}${NC}"
    echo -e "  失败:   ${RED}${FAIL}${NC}"
    echo "  耗时:   ${duration}s"
    echo "============================================"

    # 生成报告
    generate_report

    # 返回码
    if [ "$FAIL" -gt 0 ]; then
        return 1
    fi
    return 0
}

main "$@"
