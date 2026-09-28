package com.idata.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DashboardItemRequest {
    @NotNull(message = "报表ID不能为空")
    private Long reportId;

    /** 卡片标题，为空则用报表名 */
    private String title;

    private Integer posX;

    private Integer posY;

    private Integer width;

    private Integer height;

    /** 阈值告警配置 JSON: {field,operator,value,level} */
    private String alertConfig;
}
