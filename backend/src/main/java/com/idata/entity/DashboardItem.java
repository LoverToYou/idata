package com.idata.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dashboard_item")
public class DashboardItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dashboardId;

    private Long reportId;

    /** 卡片标题，为空则用报表名 */
    private String title;

    /** 栅格列位置(0-11) */
    private Integer posX;

    /** 栅格行位置 */
    private Integer posY;

    /** 卡片宽度(栅格列数) */
    private Integer width;

    /** 卡片高度(栅格行数) */
    private Integer height;

    /** 阈值告警配置 JSON: {field,operator,value,level} */
    private String alertConfig;
}
