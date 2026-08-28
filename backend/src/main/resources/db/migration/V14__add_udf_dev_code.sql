CREATE TABLE udf_dev_code (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '函数名',
    class_name VARCHAR(255) NOT NULL COMMENT '实现类全限定名',
    function_type VARCHAR(20) DEFAULT 'UDF' COMMENT 'UDF/UDAF/UDTF',
    database_name VARCHAR(100) DEFAULT 'default',
    datasource_id BIGINT COMMENT '关联数据源',
    source_code LONGTEXT NOT NULL COMMENT 'Java 源码',
    description VARCHAR(500) COMMENT '描述',
    published_definition_id BIGINT COMMENT '已发布到的 udf_definition.id',
    created_at DATETIME,
    updated_at DATETIME,
    KEY idx_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'UDF 在线开发草稿';
