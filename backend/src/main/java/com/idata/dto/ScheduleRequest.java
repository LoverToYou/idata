package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduleRequest {
    private Long id;

    @NotNull(message = "工作流ID不能为空")
    private Long workflowId;

    @NotBlank(message = "Cron表达式不能为空")
    private String cronExpression;

    private Boolean enabled;
}
