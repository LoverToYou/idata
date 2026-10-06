-- 数据源：支持 JDBC URL 直连方式
ALTER TABLE datasource_config
    ADD COLUMN jdbc_url VARCHAR(1000) COMMENT 'JDBC URL（直连方式，配置后优先使用）';
