-- 为 workflow_definition 添加 etl_type 字段
ALTER TABLE workflow_definition
    ADD COLUMN etl_type VARCHAR(50) NOT NULL DEFAULT 'DATAX' COMMENT 'ETL 类型: DATAX / ...' AFTER `status`;

-- 创建脱敏规则表
CREATE TABLE masking_rule (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(255) NOT NULL COMMENT '规则名称',
    type        VARCHAR(50)  NOT NULL COMMENT '规则类型: MASK / HASH / REPLACE / TRUNCATE / NULLIFY',
    config      TEXT COMMENT '规则配置(JSON)',
    description VARCHAR(500) COMMENT '规则描述',
    created_at  DATETIME COMMENT '创建时间',
    updated_at  DATETIME COMMENT '更新时间'
) COMMENT '脱敏规则表';
