-- 看板卡片：阈值告警配置
ALTER TABLE dashboard_item
    ADD COLUMN alert_config TEXT COMMENT '告警配置JSON: {field,operator,value,level}';
