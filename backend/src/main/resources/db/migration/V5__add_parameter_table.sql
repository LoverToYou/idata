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
