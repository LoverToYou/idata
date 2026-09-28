package com.idata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** 失败自动重试次数，0=不重试 */
    @Min(value = 0, message = "重试次数不能小于0")
    @Max(value = 100, message = "重试次数不能大于100")
    private Integer retryCount;

    /** 失败重试间隔(秒) */
    @Min(value = 1, message = "重试间隔不能小于1秒")
    @Max(value = 86400, message = "重试间隔不能超过86400秒")
    private Integer retryIntervalSeconds;
}
