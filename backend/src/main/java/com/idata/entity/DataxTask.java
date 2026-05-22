package com.idata.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("datax_task")
public class DataxTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Long readerDatasourceId;

    private String readerDatabase;

    private String readerTable;

    private String readerColumns;

    private String readerWhere;

    private Long writerDatasourceId;

    private String writerDatabase;

    private String writerTable;

    private String writerColumns;

    private String writeMode;

    private String fieldMappings;

    private Integer channel;

    private String splitPk;

    private Integer batchSize;

    private String encoding;

    private String preSql;

    private String postSql;

    private String status;

    private String configMode;

    private String scriptContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
