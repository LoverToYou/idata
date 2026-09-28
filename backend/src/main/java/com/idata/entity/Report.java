package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("report")
public class Report {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long datasourceId;

    /** 查询 SQL，支持 ${参数} 占位（取自参数管理） */
    private String sqlContent;

    /** TABLE / LINE / BAR / PIE */
    private String chartType;

    /** 图表配置 JSON: {xField,yFields,seriesField,limit} */
    private String chartConfig;

    /** 自动刷新间隔(秒)，0=不自动刷新 */
    private Integer refreshInterval;

    /** 所属文件夹ID，NULL=未分组 */
    private Long folderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
