package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("datasource_config")
public class DatasourceConfig {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type; // MYSQL / HIVE

    private String host;

    private Integer port;

    private String databaseName;

    /** JDBC URL 直连方式，配置后优先于 host/port/databaseName */
    private String jdbcUrl;

    /** 默认执行引擎：HIVE / SPARK */
    private String engine;

    /** Spark 引擎（Spark Thrift Server）的 JDBC URL */
    private String sparkJdbcUrl;

    private String username;

    private String password;

    private String props; // extra connection params as JSON

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
