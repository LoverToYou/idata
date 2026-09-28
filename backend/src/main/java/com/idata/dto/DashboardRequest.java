package com.idata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DashboardRequest {
    private Long id;

    @NotBlank(message = "看板名称不能为空")
    private String name;

    private String description;

    /** 大屏自动刷新间隔(秒)，0=不自动刷新 */
    @Min(value = 0, message = "刷新间隔不能小于0")
    @Max(value = 86400, message = "刷新间隔不能超过86400秒")
    private Integer refreshInterval;

    /** 大屏全局筛选配置 JSON */
    private String filters;

    /** 大屏是否等比缩放适配 */
    private Boolean fitScale;

    /** 所属文件夹ID，NULL=未分组 */
    private Long folderId;

    /** 卡片布局 */
    private List<DashboardItemRequest> items;
}
