package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SqlTaskVO {
    private Long id;
    private String name;
    private String description;
    private Long datasourceId;
    private String sqlContent;
    private String createdBy;
    private String datasourceType;
    private Boolean datasourceConnected;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
