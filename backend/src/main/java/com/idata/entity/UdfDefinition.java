package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("udf_definition")
public class UdfDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String className;

    private String jarFileName;

    private String jarPath;

    private Long jarSize;

    private String databaseName;

    private Long datasourceId;

    private String functionType;

    private String description;

    private String registerStatus;

    private String registerMessage;

    private String registerSql;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
