ALTER TABLE sql_task
    ADD COLUMN created_by VARCHAR(64) DEFAULT NULL COMMENT '创建人' AFTER sql_type;
