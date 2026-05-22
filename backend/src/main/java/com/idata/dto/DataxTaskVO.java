package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class DataxTaskVO {
    private Long id;
    private String name;
    private String description;

    private Long readerDatasourceId;
    private String readerDatabase;
    private String readerTable;
    private List<String> readerColumns;
    private String readerWhere;

    private Long writerDatasourceId;
    private String writerDatabase;
    private String writerTable;
    private List<String> writerColumns;
    private String writeMode;

    private List<Map<String, Object>> fieldMappings;

    private Integer channel;
    private String splitPk;
    private Integer batchSize;
    private String encoding;
    private String preSql;
    private String postSql;

    private String status;
    private String configMode;
    private String scriptContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
