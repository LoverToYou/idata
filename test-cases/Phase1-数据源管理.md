# Phase 1 — 数据源管理模块测试用例

## 环境准备

1. 启动 MySQL，创建 `idata` 数据库
2. 启动后端：`cd backend && mvn spring-boot:run`
3. 启动前端：`cd frontend && npm run dev`
4. 准备一个可用的 MySQL 实例和一个 HiveServer2 实例用于连接测试

---

## TC-101 新建数据源

| 项目 | 内容 |
|------|------|
| **前置条件** | 进入「新建数据源」页面 |
| **测试数据** | 名称=测试MySQL, 类型=MySQL, host=127.0.0.1, port=3306, db=test, user=root, pwd=root |
| **操作步骤** | 1. 填写表单 → 2. 点击「创建」 |
| **预期结果** | 创建成功，跳转回列表页，列表中出现新数据源 |
| **实际结果** | ✅ POST /api/datasource/create 返回 200，列表页出现新数据源 |
| **状态** | ✅ 通过 |

---

## TC-102 新建数据源（必填项校验）

| 项目 | 内容 |
|------|------|
| **前置条件** | 进入新建表单页面 |
| **操作步骤** | 1. 名称留空 → 2. 点击「创建」 |
| **预期结果** | 表单提示"名称不能为空"，不提交请求 |
| **实际结果** | ✅ 前端表单校验阻止提交；API 返回 `{"code":400,"message":"name: 名称不能为空"}` |
| **状态** | ✅ 通过 |

---

## TC-103 新建数据源（空密码）

| 项目 | 内容 |
|------|------|
| **前置条件** | 进入新建表单页面 |
| **操作步骤** | 1. 填写所有字段，密码留空 → 2. 点击「创建」 |
| **预期结果** | 后端拒绝，提示"密码不能为空" |
| **实际结果** | ✅ API 返回 `{"code":400,"message":"密码不能为空"}` |
| **状态** | ✅ 通过 |

---

## TC-104 编辑数据源（更新密码）

| 项目 | 内容 |
|------|------|
| **前置条件** | 列表页有一条已有数据源（id=1） |
| **操作步骤** | 1. 点击「编辑」→ 2. 修改名称和密码 → 3. 点击「保存」 |
| **预期结果** | 保存成功，列表页名称和 updatedAt 更新 |
| **实际结果** | ✅ PUT /api/datasource/update 返回 200，updatedAt 更新为当前时间 |
| **状态** | ✅ 通过 |

---

## TC-105 编辑数据源（不修改密码）

| 项目 | 内容 |
|------|------|
| **前置条件** | 列表页有一条已有数据源 |
| **操作步骤** | 1. 点击「编辑」→ 2. 修改名称，密码留空 → 3. 点击「保存」 |
| **预期结果** | 保存成功，密码保持原值不变 |
| **实际结果** | ✅ PUT 返回 200，名称更新，密码未被覆盖 |
| **状态** | ✅ 通过 |

---

## TC-106 编辑数据源（不存在 ID）

| 项目 | 内容 |
|------|------|
| **前置条件** | 数据源 id=999 不存在 |
| **操作步骤** | 直接调 API：PUT /api/datasource/update 传入 id=999 |
| **预期结果** | 返回错误提示"数据源不存在" |
| **实际结果** | ✅ `{"code":400,"message":"数据源不存在: 999"}` |
| **状态** | ✅ 通过 |

---

## TC-107 删除数据源

| 项目 | 内容 |
|------|------|
| **前置条件** | 列表页有一条已有数据源 |
| **操作步骤** | 1. 点击「删除」→ 2. 确认对话框点「确定」 |
| **预期结果** | 删除成功，列表不再显示该数据源 |
| **实际结果** | ✅ DELETE 返回 200，列表页刷新后记录消失 |
| **状态** | ✅ 通过 |

---

## TC-108 删除数据源（不存在）

| 项目 | 内容 |
|------|------|
| **前置条件** | 数据源 id=999 不存在 |
| **操作步骤** | 直接调 API：DELETE /api/datasource/999 |
| **预期结果** | 返回错误提示"数据源不存在" |
| **实际结果** | ✅ `{"code":400,"message":"数据源不存在: 999"}` |
| **状态** | ✅ 通过 |

---

## TC-109 测试连接（成功）

| 项目 | 内容 |
|------|------|
| **前置条件** | 存在一个连接信息正确的数据源配置 |
| **操作步骤** | 1. 调 API：POST /api/datasource/test-connection |
| **预期结果** | 返回 `{"code":200,"data":true}` |
| **实际结果** | ✅ 使用正确的 idata 用户连接 MySQL 成功 |
| **状态** | ✅ 通过 |

---

## TC-110 测试连接（失败 — 错误密码）

| 项目 | 内容 |
|------|------|
| **前置条件** | 数据源配置了错误密码 |
| **操作步骤** | 1. 调 API：POST /api/datasource/test-connection |
| **预期结果** | 返回错误提示，显示连接失败原因 |
| **实际结果** | ✅ `{"code":500,"message":"连接失败: Access denied for user 'root'@'localhost' (using password: YES)"}` |
| **状态** | ✅ 通过 |

---

## Bug 修复记录

### Bug 1: testConnection MySQL — "Public Key Retrieval is not allowed"

| 项目 | 内容 |
|------|------|
| **症状** | 测试 MySQL 连接时始终报 `Public Key Retrieval is not allowed` |
| **原因** | `buildJdbcUrl()` 中的 MySQL JDBC URL 缺少 `allowPublicKeyRetrieval=true` |
| **修复** | 在 `DatasourceService.java` 的 MySQL JDBC URL 模板追加 `allowPublicKeyRetrieval=true` |
| **文件** | `backend/src/main/java/com/idata/service/datasource/DatasourceService.java:117` |

### Bug 2: Update 返回陈旧数据（updatedAt 不刷新）

| 项目 | 内容 |
|------|------|
| **症状** | PUT update 后返回的 `updatedAt` 还是旧值 |
| **原因** | `updateById` 后直接返回入参 entity，未从 DB 重新查询 |
| **修复** | 改为 `return toVO(datasourceConfigMapper.selectById(request.getId()))` |
| **文件** | `backend/src/main/java/com/idata/service/datasource/DatasourceService.java:82` |

### Bug 3: MyMetaObjectHandler 不更新 updatedAt

| 项目 | 内容 |
|------|------|
| **症状** | 即使 `updateById` 后 re-select，`updatedAt` 仍不变 |
| **原因** | `strictUpdateFill()` 只在字段为 **null** 时填充，但 entity 从 DB 加载后 `updatedAt` 非 null |
| **修复** | 改用 `setFieldValByName("updatedAt", LocalDateTime.now(), metaObject)` 强制覆盖 |
| **文件** | `backend/src/main/java/com/idata/config/MyMetaObjectHandler.java:14` |

### Bug 4: 空密码返回 500 而非 400

| 项目 | 内容 |
|------|------|
| **症状** | 编辑时密码留空 → 后端返回 500 Internal Server Error |
| **原因** | `DatasourceRequest.password` 有 `@NotBlank` 校验，但 `MethodArgumentNotValidException` 未在 `GlobalExceptionHandler` 中处理 |
| **修复** | 1. 移除 `DatasourceRequest.password` 上的 `@NotBlank`；2. 在 `create()` 中手动校验密码；3. `GlobalExceptionHandler` 增加 `MethodArgumentNotValidException` 处理 |
| **文件** | `DatasourceRequest.java`, `DatasourceService.java`, `GlobalExceptionHandler.java` |

### Bug 5: 前端 testConnection 失败时弹出重复错误

| 项目 | 内容 |
|------|------|
| **症状** | testConnection 失败时弹出两个错误提示框 |
| **原因** | axios 拦截器和 UI 层 catch 块各弹一次 `ElMessage.error` |
| **修复** | 移除 axios 拦截器中 `ElMessage.error` 调用，改由各页面统一处理 |
| **文件** | `frontend/src/api/request.ts` |

### Bug 6: 删除时 ElMessageBox 取消导致未捕获异常

| 项目 | 内容 |
|------|------|
| **症状** | 点击删除确认框的「取消」→ 控制台报未捕获 Promise rejection |
| **原因** | `handleDelete` 的 `await ElMessageBox.confirm()` 在取消时会 reject，但没有 catch |
| **修复** | 在 `handleDelete` 中加入 try-catch |
| **文件** | `frontend/src/views/datasource/DatasourceList.vue` |

### Bug 7: TestConnectionService 中多余的 import

| 项目 | 内容 |
|------|------|
| **症状** | 编译无影响但代码冗余 |
| **原因** | `DatasourceService` 中 import 了未使用的 `LambdaQueryWrapper`, `JsonProcessingException`, `ObjectMapper` |
| **修复** | 移除未使用的 import |
| **文件** | `DatasourceService.java` |

---

## 接口验证结果一览

| # | 接口 | 方法 | 状态 |
|---|------|------|------|
| 1 | /api/datasource/list | GET | ✅ 通过 |
| 2 | /api/datasource/{id} | GET | ✅ 通过 |
| 3 | /api/datasource/{id} (不存在) | GET | ✅ 通过 (400) |
| 4 | /api/datasource/create | POST | ✅ 通过 |
| 5 | /api/datasource/create (空密码) | POST | ✅ 通过 (400) |
| 6 | /api/datasource/update | PUT | ✅ 通过 |
| 7 | /api/datasource/update (空密码) | PUT | ✅ 通过 |
| 8 | /api/datasource/update (不存在) | PUT | ✅ 通过 (400) |
| 9 | /api/datasource/{id} | DELETE | ✅ 通过 |
| 10 | /api/datasource/{id} (不存在) | DELETE | ✅ 通过 (400) |
| 11 | /api/datasource/test-connection | POST | ✅ 通过 |
| 12 | /api/datasource/test-connection/{id} | POST | ✅ 通过 |

## 接口自动化测试（curl 示例）

```bash
# 1. 创建数据源
curl -X POST http://localhost:8088/api/datasource/create \
  -H "Content-Type: application/json" \
  -d '{"name":"测试MySQL","type":"MYSQL","host":"127.0.0.1","port":3306,"databaseName":"test","username":"root","password":"root"}'

# 2. 列表查询
curl http://localhost:8088/api/datasource/list

# 3. 测试连接
curl -X POST http://localhost:8088/api/datasource/test-connection \
  -H "Content-Type: application/json" \
  -d '{"type":"MYSQL","host":"127.0.0.1","port":3306,"username":"your_user","password":"your_pwd"}'

# 4. 测试连接 by id
curl -X POST http://localhost:8088/api/datasource/test-connection/1

# 5. 更新
curl -X PUT http://localhost:8088/api/datasource/update \
  -H "Content-Type: application/json" \
  -d '{"id":1,"name":"测试MySQL-2","type":"MYSQL","host":"127.0.0.1","port":3306,"databaseName":"test","username":"root","password":"newpwd"}'

# 6. Hive 元数据 - 数据库列表
curl http://localhost:8088/api/datasource/1/hive/databases

# 7. Hive 元数据 - 表列表
curl http://localhost:8088/api/datasource/1/hive/default/tables

# 8. 删除
curl -X DELETE http://localhost:8088/api/datasource/1
```
