package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DashboardVO {
    private Long id;
    private String name;
    private String description;
    private Integer refreshInterval;
    private String filters;
    private Boolean fitScale;
    private Long folderId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DashboardItemVO> items;
}
