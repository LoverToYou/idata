package com.idata.dto;

import lombok.Data;

/**
 * 连接测试请求：支持两种方式——
 * 1) JDBC URL 直连（jdbcUrl，优先）
 * 2) 类型 + 主机 + 端口（+ 数据库名）
 */
@Data
public class ConnectionTestRequest {
    /** MYSQL / HIVE，JDBC URL 方式下可省略（按 URL 前缀推断） */
    private String type;

    private String host;

    private Integer port;

    private String databaseName;

    /** JDBC URL 直连方式（可选，填写后优先使用） */
    private String jdbcUrl;

    /** Spark 引擎（Spark Thrift Server）的 JDBC URL（测试 SPARK 引擎时使用） */
    private String sparkJdbcUrl;

    /** 指定测试的引擎：HIVE / SPARK（为空用数据源默认） */
    private String engine;

    /** 已有数据源 ID：测试 SPARK 引擎时可据此取 sparkJdbcUrl */
    private Long datasourceId;

    private String username;

    private String password;
}
