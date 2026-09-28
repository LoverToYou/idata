package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dashboard")
public class Dashboard {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 大屏自动刷新间隔(秒)，0=不自动刷新 */
    private Integer refreshInterval;

    /** 大屏全局筛选配置 JSON */
    private String filters;

    /** 大屏是否按 1920x1080 设计稿等比缩放适配 */
    private Boolean fitScale;

    /** 所属文件夹ID，NULL=未分组 */
    private Long folderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
