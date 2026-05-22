package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_parameter")
public class Parameter {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String paramName;

    private String paramValue;

    private String paramType; // STATIC / DYNAMIC

    private String expression;

    private String description;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
