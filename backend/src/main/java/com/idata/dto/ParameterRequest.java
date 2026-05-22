package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ParameterRequest {
    private Long id;

    @NotBlank(message = "参数名称不能为空")
    private String paramName;

    private String paramValue;

    @NotBlank(message = "参数类型不能为空")
    private String paramType; // STATIC / DYNAMIC

    private String expression;

    private String description;

    private Boolean enabled;
}
