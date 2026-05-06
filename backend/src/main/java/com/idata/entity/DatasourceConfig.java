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

    private String username;

    private String password;

    private String props; // extra connection params as JSON

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
