ALTER TABLE udf_dev_code
    ADD COLUMN java_version VARCHAR(10) DEFAULT '17' COMMENT '编译目标 Java 版本 8/11/17',
    ADD COLUMN engine_type VARCHAR(20) DEFAULT 'HIVE' COMMENT '引擎类型 HIVE/SPARK',
    ADD COLUMN engine_version VARCHAR(50) DEFAULT '4.0.0' COMMENT '引擎版本';
