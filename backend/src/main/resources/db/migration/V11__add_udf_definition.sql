CREATE TABLE udf_definition (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL COMMENT '函数名',
    class_name       VARCHAR(500) NOT NULL COMMENT 'UDF 全限定类名',
    jar_file_name    VARCHAR(255) NOT NULL COMMENT '原始 jar 文件名(展示用)',
    jar_path         VARCHAR(1000) NOT NULL COMMENT 'jar 本地绝对路径',
    jar_size         BIGINT DEFAULT 0 COMMENT 'jar 大小(字节)',
    database_name    VARCHAR(100) DEFAULT 'default' COMMENT '注册目标数据库',
    datasource_id    BIGINT NOT NULL COMMENT '关联 Hive 数据源ID',
    function_type    VARCHAR(20) DEFAULT 'UDF' COMMENT 'UDF/UDAF/UDTF',
    description      VARCHAR(500) COMMENT '函数描述',
    register_status  VARCHAR(20) DEFAULT 'UNREGISTERED' COMMENT 'UNREGISTERED/REGISTERED/FAILED',
    register_message VARCHAR(2000) COMMENT '注册结果信息',
    register_sql     VARCHAR(2000) COMMENT '注册时执行的SQL',
    created_at       DATETIME COMMENT '创建时间',
    updated_at       DATETIME COMMENT '更新时间',
    UNIQUE KEY uk_ds_db_name (datasource_id, database_name, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'Hive UDF 定义表';
