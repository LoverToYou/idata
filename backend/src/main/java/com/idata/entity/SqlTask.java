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

    /** 执行引擎：HIVE / SPARK（空=跟随数据源默认）；允许更新为空 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String engine;

    private String createdBy;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
