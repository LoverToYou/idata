package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("folder")
public class Folder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 父文件夹ID，NULL=根目录 */
    private Long parentId;

    /** REPORT / DASHBOARD */
    private String bizType;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
