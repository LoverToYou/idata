package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_manage")
public class FileManage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private String fileExt;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
