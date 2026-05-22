package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("masking_rule")
public class MaskingRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type; // MASK / HASH / REPLACE / TRUNCATE / NULLIFY

    private String config; // rule configuration as JSON

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
