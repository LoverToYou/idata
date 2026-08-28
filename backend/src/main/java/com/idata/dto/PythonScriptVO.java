package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PythonScriptVO {
    private Long id;
    private String name;
    private String description;
    private String content;
    private Integer timeoutSeconds;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
