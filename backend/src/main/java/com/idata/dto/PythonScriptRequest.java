package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PythonScriptRequest {
    private Long id;

    @NotBlank(message = "脚本名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "脚本内容不能为空")
    private String content;

    private Integer timeoutSeconds;

    private String createdBy;
}
