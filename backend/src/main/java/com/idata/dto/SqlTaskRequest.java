package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlTaskRequest {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    private Long datasourceId;

    private String sqlContent;
}
