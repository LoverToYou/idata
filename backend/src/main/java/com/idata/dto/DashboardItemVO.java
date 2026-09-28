package com.idata.dto;

import lombok.Data;

@Data
public class DashboardItemVO {
    private Long id;
    private Long reportId;
    private String reportName;
    private String chartType;
    private String chartConfig;
    private String title;
    private Integer posX;
    private Integer posY;
    private Integer width;
    private Integer height;
    private String alertConfig;
}
