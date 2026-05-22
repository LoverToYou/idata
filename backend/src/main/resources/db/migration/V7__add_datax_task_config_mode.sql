ALTER TABLE datax_task
    ADD COLUMN config_mode    VARCHAR(20) DEFAULT 'UI' COMMENT '配置模式: UI / SCRIPT' AFTER `status`,
    ADD COLUMN script_content TEXT COMMENT '脚本模式下DataX JSON内容' AFTER `config_mode`;
