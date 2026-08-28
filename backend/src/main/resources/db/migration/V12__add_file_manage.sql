CREATE TABLE file_manage (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name   VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path   VARCHAR(1000) NOT NULL COMMENT 'HDFS 完整 URI',
    file_size   BIGINT DEFAULT 0 COMMENT '字节数',
    file_ext    VARCHAR(20) COMMENT '小写扩展名，如 jar',
    description VARCHAR(500) COMMENT '备注',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间',
    KEY idx_ext (file_ext)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'HDFS 文件管理表';
