-- =============================================================
-- IDATA 数据库完整建表语句
-- 说明：根据 Flyway 迁移脚本 (V1~V9) 合并整理
-- =============================================================

-- ----------------------------
-- 1. 数据源配置表 (V1)
-- ----------------------------
CREATE TABLE datasource_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL COMMENT '数据源名称',
    type          VARCHAR(20) NOT NULL COMMENT 'MYSQL / HIVE',
    host          VARCHAR(255) COMMENT '主机地址',
    port          INT COMMENT '端口',
    database_name VARCHAR(100) COMMENT '默认数据库',
    username      VARCHAR(100) COMMENT '用户名',
    password      VARCHAR(255) COMMENT '密码(加密存储)',
    props         TEXT COMMENT '额外连接参数(JSON)',
    created_at    DATETIME COMMENT '创建时间',
    updated_at    DATETIME COMMENT '更新时间'
) COMMENT '数据源配置表';

-- ----------------------------
-- 2. 工作流定义表 (V1 + V4)
-- ----------------------------
CREATE TABLE workflow_definition (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '描述',
    dag_json    LONGTEXT NOT NULL COMMENT 'DAG 完整定义(节点+边) JSON',
    status      VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    etl_type    VARCHAR(50) NOT NULL DEFAULT 'DATAX' COMMENT 'ETL 类型: DATAX / ...',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间'
) COMMENT '工作流定义表';

-- ----------------------------
-- 3. 工作流实例表 (V1)
-- ----------------------------
CREATE TABLE workflow_instance (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id   BIGINT NOT NULL COMMENT '工作流定义ID',
    status        VARCHAR(20) COMMENT 'RUNNING / SUCCESS / FAILED',
    started_at    DATETIME COMMENT '开始时间',
    finished_at   DATETIME COMMENT '结束时间',
    triggered_by  VARCHAR(50) COMMENT 'CRON / MANUAL',
    error_message TEXT COMMENT '错误信息',
    created_at    DATETIME COMMENT '创建时间'
) COMMENT '工作流实例表';

-- ----------------------------
-- 4. 节点执行记录表 (V1 + V8)
-- ----------------------------
CREATE TABLE node_execution_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id   BIGINT NOT NULL COMMENT '工作流实例ID',
    node_id       VARCHAR(100) NOT NULL COMMENT 'DAG 节点ID',
    node_name     VARCHAR(200) COMMENT '节点名称',
    status        VARCHAR(20) COMMENT 'WAITING / RUNNING / SUCCESS / FAILED',
    started_at    DATETIME COMMENT '开始时间',
    finished_at   DATETIME COMMENT '结束时间',
    datax_pid     INT COMMENT 'DataX 进程PID',
    log_path      VARCHAR(500) COMMENT 'DataX 日志路径',
    datax_json    TEXT DEFAULT NULL COMMENT 'DataX job JSON configuration',
    output_log    LONGTEXT DEFAULT NULL COMMENT 'Execution stdout/stderr output',
    error_message TEXT COMMENT '错误信息',
    created_at    DATETIME COMMENT '创建时间'
) COMMENT '节点执行记录表';

-- ----------------------------
-- 5. 调度配置表 (V1)
-- ----------------------------
CREATE TABLE schedule_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL COMMENT '工作流定义ID',
    cron_expression VARCHAR(100) COMMENT 'Cron 表达式',
    enabled         BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at      DATETIME COMMENT '创建时间',
    updated_at      DATETIME COMMENT '更新时间'
) COMMENT '调度配置表';

-- ----------------------------
-- 6. SQL 任务表 (V2 + V3 + V9)
-- ----------------------------
CREATE TABLE sql_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(200) NOT NULL COMMENT '任务名称',
    description   TEXT COMMENT '任务描述',
    datasource_id BIGINT COMMENT '关联数据源ID',
    sql_content   TEXT NOT NULL COMMENT 'SQL 内容',
    sql_type      VARCHAR(50) DEFAULT 'OTHER' COMMENT 'SQL 类型: SELECT / INSERT / UPDATE / DELETE / OTHER',
    status        VARCHAR(20) DEFAULT 'DRAFT' COMMENT '任务状态: DRAFT / PUBLISHED',
    created_by    VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    created_at    DATETIME COMMENT '创建时间',
    updated_at    DATETIME COMMENT '更新时间'
) COMMENT 'SQL 任务表';

-- ----------------------------
-- 7. 脱敏规则表 (V4)
-- ----------------------------
CREATE TABLE masking_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(255) NOT NULL COMMENT '规则名称',
    type        VARCHAR(50)  NOT NULL COMMENT '规则类型: MASK / HASH / REPLACE / TRUNCATE / NULLIFY',
    config      TEXT COMMENT '规则配置(JSON)',
    description VARCHAR(500) COMMENT '规则描述',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间'
) COMMENT '脱敏规则表';

-- ----------------------------
-- 8. 系统参数表 (V5)
-- ----------------------------
CREATE TABLE sys_parameter (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    param_name  VARCHAR(100) NOT NULL COMMENT '参数名称',
    param_value VARCHAR(500) COMMENT '参数值（静态参数）',
    param_type  VARCHAR(20)  NOT NULL DEFAULT 'STATIC' COMMENT '参数类型: STATIC / DYNAMIC',
    expression  VARCHAR(200) COMMENT '动态参数表达式（如 today, yyyyMMdd）',
    description VARCHAR(500) COMMENT '参数描述',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_param_name (param_name)
) COMMENT='系统参数表';

-- ----------------------------
-- 9. DataX 任务表 (V6 + V7)
-- ----------------------------
CREATE TABLE datax_task (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL COMMENT '任务名称',
    description          TEXT COMMENT '任务描述',
    -- 读取端
    reader_datasource_id BIGINT COMMENT '源数据源ID',
    reader_database      VARCHAR(100) COMMENT '源数据库',
    reader_table         VARCHAR(100) COMMENT '源表',
    reader_columns       TEXT COMMENT '源列(JSON数组)',
    reader_where         VARCHAR(500) COMMENT '过滤条件',
    -- 写入端
    writer_datasource_id BIGINT COMMENT '目标数据源ID',
    writer_database      VARCHAR(100) COMMENT '目标数据库',
    writer_table         VARCHAR(100) COMMENT '目标表',
    writer_columns       TEXT COMMENT '目标列(JSON数组)',
    write_mode           VARCHAR(20) DEFAULT 'insert' COMMENT '写入模式: INSERT / OVERWRITE',
    -- 字段映射
    field_mappings       TEXT COMMENT '字段映射JSON: [{readerColumn,writerColumn,targetType,maskingRuleId}]',
    -- 高级配置
    channel              INT DEFAULT 1 COMMENT '并发通道数',
    split_pk             VARCHAR(100) COMMENT '分片键',
    batch_size           INT DEFAULT 1000 COMMENT '批次大小',
    encoding             VARCHAR(20) DEFAULT 'UTF-8' COMMENT '编码',
    pre_sql              TEXT COMMENT '前置SQL',
    post_sql             TEXT COMMENT '后置SQL',
    -- 状态
    status               VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    config_mode          VARCHAR(20) DEFAULT 'UI' COMMENT '配置模式: UI / SCRIPT',
    script_content       TEXT COMMENT '脚本模式下DataX JSON内容',
    created_at           DATETIME COMMENT '创建时间',
    updated_at           DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'DataX 任务表';
