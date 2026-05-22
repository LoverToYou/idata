package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowRequest {
    private Long id;

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private String dagJson;

    private String status;

    private String etlType;
}
