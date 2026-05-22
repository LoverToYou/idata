package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ParameterVO {
    private Long id;
    private String paramName;
    private String paramValue;
    private String paramType;
    private String expression;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
