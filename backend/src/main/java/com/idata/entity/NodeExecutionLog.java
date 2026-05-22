package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("node_execution_log")
public class NodeExecutionLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instanceId;

    private String nodeId;

    private String nodeName;

    private String status; // WAITING / RUNNING / SUCCESS / FAILED

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer dataxPid;

    private String logPath;

    private String dataxJson;

    private String outputLog;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
