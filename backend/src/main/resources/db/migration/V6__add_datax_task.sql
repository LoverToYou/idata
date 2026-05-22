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
    created_at           DATETIME COMMENT '创建时间',
    updated_at           DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'DataX 任务表';
