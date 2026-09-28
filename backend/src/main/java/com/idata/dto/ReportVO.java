package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportVO {
    private Long id;
    private String name;
    private String description;
    private Long datasourceId;
    private String datasourceName;
    private String sqlContent;
    private String chartType;
    private String chartConfig;
    private Integer refreshInterval;
    private Long folderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
