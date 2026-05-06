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

CREATE TABLE workflow_definition (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '描述',
    dag_json    LONGTEXT NOT NULL COMMENT 'DAG 完整定义(节点+边) JSON',
    status      VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间'
) COMMENT '工作流定义表';

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
    error_message TEXT COMMENT '错误信息',
    created_at    DATETIME COMMENT '创建时间'
) COMMENT '节点执行记录表';

CREATE TABLE schedule_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     BIGINT NOT NULL COMMENT '工作流定义ID',
    cron_expression VARCHAR(100) COMMENT 'Cron 表达式',
    enabled         BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at      DATETIME COMMENT '创建时间',
    updated_at      DATETIME COMMENT '更新时间'
) COMMENT '调度配置表';
