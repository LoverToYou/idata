package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstanceVO {
    private Long id;
    private Long workflowId;
    private String workflowName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String triggeredBy;
    private String errorMessage;
    private LocalDateTime createdAt;
}
