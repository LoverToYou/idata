package com.idata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportRequest {
    private Long id;

    @NotBlank(message = "报表名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "请选择数据源")
    private Long datasourceId;

    @NotBlank(message = "查询SQL不能为空")
    private String sqlContent;

    /** TABLE / LINE / BAR / PIE */
    private String chartType;

    /** 图表配置 JSON 字符串 */
    private String chartConfig;

    /** 自动刷新间隔(秒)，0=不自动刷新 */
    @Min(value = 0, message = "刷新间隔不能小于0")
    @Max(value = 86400, message = "刷新间隔不能超过86400秒")
    private Integer refreshInterval;

    /** 所属文件夹ID，NULL=未分组 */
    private Long folderId;
}
