# Phase 4: 工作流调度 & 监控 — 测试用例

## 前置条件

- 后端服务运行在 `http://localhost:8088`
- 存在已发布的工作流（含 DAG 节点）

---

## 1. 调度配置 CRUD (后端 API)

| 编号 | 用例名 | 操作 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 4-01 | 创建调度 | `POST /api/schedule/create {"workflowId":2,"cronExpression":"0 0 8 * * ?"}` | 返回 200，enabled=true | 正确返回含 workflowName 的调度配置 | ✅ |
| 4-02 | 创建调度（无效 cron） | `POST /api/schedule/create {"workflowId":2,"cronExpression":"invalid"}` | 返回 400 错误 | - | 🔲 待测 |
| 4-03 | 创建调度（工作流不存在） | `POST /api/schedule/create {"workflowId":999,...}` | 返回 400 错误 | - | 🔲 待测 |
| 4-04 | 列表查询 | `GET /api/schedule/list` | 返回调度列表（含 workflowName） | 正确返回含 workflowName 的列表 | ✅ |
| 4-05 | 按工作流查询 | `GET /api/schedule/workflow/{workflowId}` | 返回该工作流的调度 | - | 🔲 待测 |
| 4-06 | 禁用调度 | `PUT /api/schedule/1/toggle?enabled=false` | enabled 变为 false | 返回 200, data=null | ✅ |
| 4-07 | 启用调度 | `PUT /api/schedule/1/toggle?enabled=true` | enabled 变为 true | - | 🔲 待测 |
| 4-08 | 删除调度 | `DELETE /api/schedule/1` | 删除成功 | 返回 200 | ✅ |

**结论**: 调度配置 CRUD 正常工作。

---

## 2. 工作流执行与实例管理 (后端 API)

| 编号 | 用例名 | 操作 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 4-09 | 运行带 DAG 节点的工作流 | `POST /api/monitor/workflow/{id}/run` | 成功执行，实例状态 SUCCESS | 返回 instanceId=1, status=SUCCESS, triggeredBy=MANUAL | ✅ |
| 4-10 | 运行空 DAG 的工作流 | 创建 DAG 为 {} 的工作流后运行 | 返回 400 错误 | 返回 "工作流DAG定义为空" | ✅ |
| 4-11 | 运行未发布的工作流 | `POST /api/monitor/workflow/{draft}/run` | 可以运行（DagExecutor 不检查状态） | - | 🔲 待测 |
| 4-12 | 实例列表 | `GET /api/monitor/instances` | 返回实例列表 | 正确返回含 workflowName 的列表 | ✅ |
| 4-13 | 实例详情 | `GET /api/monitor/instances/{id}` | 返回实例详情 | 正确返回 | ✅ |
| 4-14 | 节点执行日志 | `GET /api/monitor/instances/{id}/nodes` | 返回节点日志列表 | 正确返回 2 条日志（MySQL Reader SUCCESS, Hive Writer SUCCESS） | ✅ |
| 4-15 | 带 workflowId 过滤 | `GET /api/monitor/instances?workflowId={id}` | 返回该工作流的实例 | - | 🔲 待测 |

**结论**: 工作流执行引擎和实例管理正常工作，支持拓扑排序执行和状态记录。

---

## 3. 前端页面 (手动测试)

| 编号 | 用例名 | 操作步骤 | 预期结果 | 实际结果 | 状态 |
|------|--------|----------|----------|----------|------|
| 4-16 | 监控看板首页 | 访问 `/monitor` | 显示统计卡片 + 实例列表 | - | 🔲 待测 |
| 4-17 | 实例列表显示 | 监控页面查看实例表格 | 列出执行历史 | - | 🔲 待测 |
| 4-18 | 查看实例详情 | 点击实例的详情按钮 | 弹出详情对话框 | - | 🔲 待测 |
| 4-19 | 查看节点日志 | 点击节点的日志按钮 | 显示时间线日志 | - | 🔲 待测 |
| 4-20 | 运行工作流（列表页） | 在工作流列表点击"运行" | 触发执行并刷新 | - | 🔲 待测 |
| 4-21 | 运行工作流（编辑器页） | 在编辑器点击"运行" | 触发执行 | - | 🔲 待测 |
| 4-22 | 发布工作流 | 在工作流列表点击"发布" | 确认后发布 | - | 🔲 待测 |

---

## 4. 已知限制

| 编号 | 问题 | 说明 |
|------|------|------|
| B-09 | DagExecutor 暂为模拟执行 | 节点实际执行是 stub，未来需对接 DataXRunner |
| B-10 | 无 Quartz 实际触发 | ScheduleService 存储配置但不调用 Quartz scheduler 实际触发 |
| B-11 | 无并发控制 | 同时多次运行同一工作流会创建多个实例 |
| B-12 | 无失败重试机制 | 节点失败直接标记 FAILED，不会自动重试 |

## 测试环境信息

- 后端版本: Spring Boot 3.2.5 + JDK 25
- 数据库: MySQL 8.0
- 测试日期: 2026-04-30
- 测试工具: curl / 浏览器手动测试
