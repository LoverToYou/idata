package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_instance")
public class WorkflowInstance {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    private String status; // RUNNING / SUCCESS / FAILED

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String triggeredBy; // CRON / MANUAL

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
