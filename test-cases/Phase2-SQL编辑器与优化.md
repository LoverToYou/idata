# Phase 2: SQL 编辑器 & 优化 — 测试用例

## 前置条件

- 后端服务运行在 `http://localhost:8088`
- 存在至少一个 MySQL 数据源（ID=4, name=ces, type=MYSQL, database=mysql）

---

## 1. SQL 格式化 (POST /api/sql/format)

| 编号 | 用例名 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 2-01 | 格式化简单 SELECT | `{"sql": "select * from user where id = 1 order by name desc"}` | 返回格式化后的 SQL | 返回 `SELECT * \nFROM user \nWHERE id = 1 \nORDER BY name desc` | ✅ |
| 2-02 | 格式化空 SQL | `{"sql": ""}` | 返回错误提示 | - | 🔲 未测 |
| 2-03 | 格式化复杂 SQL | 包含 JOIN/子查询的 SQL | 正确格式化 | - | 🔲 未测 |

**结论**: 格式化功能基础可用。

---

## 2. SQL 解析分析 (POST /api/sql/analyze)

| 编号 | 用例名 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 2-04 | 解析 SELECT * 无 WHERE | `SELECT * FROM users` | 检测到 SELECT * + 缺少 WHERE | 返回 WARNING: 避免使用 SELECT\* + WARNING: 缺少 WHERE 条件 | ✅ |
| 2-05 | 解析显式 JOIN 查询 | `SELECT * FROM users u JOIN orders o ON u.id = o.user_id WHERE u.status = 'active' ORDER BY o.created_at` | 正确识别表、JOIN 类型 | queryType=OTHER, tables/columns 为 null（Calcite 限制） | ⚠️ 已知限制 |
| 2-06 | 解析隐式 JOIN(逗号) | `SELECT u.name, o.order_id FROM users u, orders o WHERE u.id = o.user_id` | 检测到逗号 JOIN + 可接受的 WHERE | 正确识别 users/orders 表，COMMA JOIN，无笛卡尔积警告 | ✅ |
| 2-07 | 解析笛卡尔积(无 WHERE) | `SELECT u.name, o.order_id FROM users u, orders o` | 检测到笛卡尔积 | 返回 ERROR: 笛卡尔积 JOIN | ✅ |
| 2-08 | 解析 SELECT 带 WHERE/ORDER BY | `SELECT id, name FROM users WHERE status = 1 ORDER BY created_at DESC` | 正确识别表和字段 | queryType=OTHER, tables/columns 为 null（Calcite 限制） | ⚠️ 已知限制 |
| 2-09 | 解析子查询 | `SELECT * FROM (SELECT id, name FROM users) t WHERE t.id = 1` | 识别子查询 | 正确返回 SELECT\* 警告，queryType=SELECT | ✅ |
| 2-10 | 解析 INSERT 语句 | `INSERT INTO users (name, age) VALUES ('test', 18)` | 识别 INSERT 类型 | queryType=INSERT | ✅ |
| 2-11 | 解析无效 SQL | `SELECTT * FORM users` | 返回 valid=false | 返回 valid=false + 错误信息 | ✅ |
| 2-12 | 解析带限定符的表名 | `SELECT * FROM mysql.user` | 可能解析失败 | 解析失败：Calcite 不支持 `db.table` 格式 | ⚠️ 已知限制 |

**结论**: Calcite 解析器对显式 JOIN（`JOIN ... ON`）、ORDER BY 子句、限定表名（`db.table`）的支持有限，解析为 `OTHER` 类型且不提取表字段。隐式 JOIN（逗号分隔 FROM）和简单查询工作正常。

---

## 3. SQL 执行 (POST /api/sql/execute)

| 编号 | 用例名 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 2-13 | 执行常量查询 | `datasourceId=4, sql="SELECT 1 as val, 'hello' as msg"` | 返回结果 | 正确返回 1 行数据: val=1, msg=hello，耗时 6ms | ✅ |
| 2-14 | 执行真实表查询 | `datasourceId=4, sql="SELECT * FROM mysql.user LIMIT 3"` | 返回 3 行数据 | 正确返回 3 行数据，51 列，耗时 11ms | ✅ |
| 2-15 | 未选择数据源 | `datasourceId=null` | 返回错误提示 | - | 🔲 前端验证 |
| 2-16 | SQL 为空 | `sql=""` | 返回错误提示 | - | 🔲 前端验证 |
| 2-17 | 数据源不存在 | `datasourceId=999` | 返回错误信息 | 返回 500: "数据源不存在: 999" | ✅ (但建议改为 4xx) |
| 2-18 | 目标表不存在 | `sql="SELECT * FROM non_existent_table"` | 返回错误信息 | - | 🔲 未测 |

**结论**: SQL 执行功能基础可用，数据返回包括 datetime 类型、null 值等均可正确处理。

---

## 4. 执行计划 (POST /api/sql/explain)

| 编号 | 用例名 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 2-19 | EXPLAIN 常量查询 | `datasourceId=4, sql="SELECT 1"` | 返回计划 | 正确返回 "No tables used"，耗时 6ms | ✅ |
| 2-20 | EXPLAIN 真实表 | `datasourceId=4, sql="SELECT * FROM mysql.user"` | 返回全表扫描行 | type=ALL, rows=7, 全表扫描 | ✅ |
| 2-21 | 数据源不存在 | `datasourceId=999` | 返回错误 | 返回 rawPlan="EXPLAIN 失败: 数据源不存在: 1" | ✅ |

**结论**: EXPLAIN 功能正常工作，MySQL 输出被正确解析为结构化行。

---

## 5. 全部分析 (POST /api/sql/full-analyze)

| 编号 | 用例名 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|--------|------|----------|----------|------|
| 2-22 | 全部分析 SELECT * | `datasourceId=4, sql="SELECT * FROM mysql.user LIMIT 5"` | 语法分析 + 计划 + 建议 | 分析(valid=false 因限定表名)，但 EXPLAIN 成功返回全表扫描建议 | ⚠️ 部分成功 |
| 2-23 | 全部分分析简单查询 | `datasourceId=4, sql="SELECT 1"` | 全部分析 | - | 🔲 未测 |

---

## 6. 前端页面验证 (手动测试)

| 编号 | 用例名 | 操作步骤 | 预期结果 | 实际结果 | 状态 |
|------|--------|----------|----------|----------|------|
| 2-24 | 页面加载 | 打开 `/sql-editor` | 编辑器渲染 + 数据源下拉列表 | - | 🔲 前端验证 |
| 2-25 | 切换数据源 | 选择不同数据源 | 选中值更新 | - | 🔲 前端验证 |
| 2-26 | 编辑 SQL | 在 Monaco 编辑器中输入 SQL | 语法高亮、自动补全 | - | 🔲 前端验证 |
| 2-27 | Ctrl+Enter 运行 | 按 Ctrl+Enter | 执行 SQL，切换到结果 Tab | - | 🔲 前端验证 |
| 2-28 | 点击"格式化" | 在工具栏点击格式化 | SQL 被格式化 | - | 🔲 前端验证 |
| 2-29 | 点击"执行计划" | 点击执行计划 | 切换到计划 Tab 显示 EXPLAIN 结果 | - | 🔲 前端验证 |
| 2-30 | 点击"全部分析" | 点击全部分析 | 切换到优化建议 Tab | - | 🔲 前端验证 |
| 2-31 | 选中文本后执行 | 选中部分 SQL 后运行 | 仅执行选中部分 | - | 🔲 前端验证 |
| 2-32 | 空 SQL 执行 | 不输入 SQL 直接点运行 | 弹出警告提示 | - | 🔲 前端验证 |

---

## 已知问题与限制

| 编号 | 问题描述 | 影响 | 建议修复 |
|------|----------|------|----------|
| B-01 | Calcite 解析显式 JOIN（`JOIN ... ON`）时无法提取表名/字段 | 分析信息 Tab 中表名和 JOIN 类型为空 | 升级解析逻辑或使用 JSqlParser 替换 |
| B-02 | Calcite 解析带 ORDER BY 的查询时 queryType 返回 "OTHER" | SQL 类型识别不准确 | 同上 |
| B-03 | Calcite 不支持 `db.table` 限定表名 | 带库名称的表名解析失败 | 预处理去除库名限定符 |
| B-04 | 数据源不存在时返回 HTTP 500 而非 4xx | 不符合 REST 规范 | DatasourceService 应抛出自定义异常，Controller 返回 404 |
| B-05 | 前端页面尚未完成端到端验证 | 功能不可靠 | 需要浏览器中逐一验证 |

## 测试环境信息

- 后端版本: Spring Boot 3.2.5 + JDK 25
- 数据库: MySQL 8.0.x (target datasource: mysql.user)
- 测试日期: 2026-04-30
- 测试工具: curl / 手动
