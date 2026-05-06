CREATE TABLE sql_task (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(200) NOT NULL COMMENT '任务名称',
    description   TEXT COMMENT '任务描述',
    datasource_id BIGINT COMMENT '关联数据源ID',
    sql_content   TEXT NOT NULL COMMENT 'SQL 内容',
    created_at    DATETIME COMMENT '创建时间',
    updated_at    DATETIME COMMENT '更新时间'
) COMMENT 'SQL 任务表';
