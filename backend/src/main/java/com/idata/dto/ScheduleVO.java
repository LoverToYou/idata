package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduleVO {
    private Long id;
    private Long workflowId;
    private String workflowName;
    private String cronExpression;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
