-- 清空旧 python 脚本与执行记录（模块重构：脚本执行 → Python 脚本）
DELETE FROM python_run;
DELETE FROM python_script;

-- python_script 改造：去掉脚本自身 cron 调度（调度统一走 定时调度→工作流），加发布状态与创建人
ALTER TABLE python_script
    DROP COLUMN cron_expression,
    DROP COLUMN cron_enabled,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    ADD COLUMN created_by VARCHAR(100) COMMENT '创建人';

-- 删除 UDF 在线开发草稿表（模块删除）
DROP TABLE udf_dev_code;
