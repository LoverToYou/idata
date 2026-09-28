-- 报表系统：报表定义表
CREATE TABLE report (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(200) NOT NULL COMMENT '报表名称',
    description      VARCHAR(500) COMMENT '报表描述',
    datasource_id    BIGINT NOT NULL COMMENT '数据源ID',
    sql_content      MEDIUMTEXT NOT NULL COMMENT '查询SQL(支持参数占位)',
    chart_type       VARCHAR(20) NOT NULL DEFAULT 'TABLE' COMMENT '图表类型: TABLE/LINE/BAR/PIE',
    chart_config     TEXT COMMENT '图表配置JSON: {xField,yFields,seriesField,limit}',
    refresh_interval INT NOT NULL DEFAULT 0 COMMENT '自动刷新间隔(秒), 0=不自动刷新',
    created_at       DATETIME COMMENT '创建时间',
    updated_at       DATETIME COMMENT '更新时间',
    KEY idx_report_datasource (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '报表定义表';
