package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowVO {
    private Long id;
    private String name;
    private String description;
    private String dagJson;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
