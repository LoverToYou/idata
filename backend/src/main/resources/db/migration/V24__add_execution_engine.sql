-- 执行引擎切换：一个 Hive 数据源可分别走 HiveServer2（Hive 引擎）或 Spark Thrift Server（Spark 引擎）
ALTER TABLE datasource_config
    ADD COLUMN engine VARCHAR(20) NOT NULL DEFAULT 'HIVE' COMMENT '默认执行引擎: HIVE / SPARK',
    ADD COLUMN spark_jdbc_url VARCHAR(1000) COMMENT 'Spark 引擎(Spark Thrift Server)的 JDBC URL';

-- SQL 任务记住所用引擎（空=跟随数据源默认引擎）
ALTER TABLE sql_task
    ADD COLUMN engine VARCHAR(20) COMMENT '执行引擎: HIVE / SPARK（空=跟随数据源默认）';
