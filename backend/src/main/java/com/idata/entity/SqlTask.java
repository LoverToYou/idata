package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sql_task")
public class SqlTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long datasourceId;

    private String sqlContent;

    private String sqlType;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
