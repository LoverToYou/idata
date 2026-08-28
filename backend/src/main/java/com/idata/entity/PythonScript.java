package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("python_script")
public class PythonScript {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String content;

    private Integer timeoutSeconds;

    private String status;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
