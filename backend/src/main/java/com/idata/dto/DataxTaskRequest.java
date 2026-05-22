package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DataxTaskRequest {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
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

    private String configMode;

    private String scriptContent;

    private Integer channel;
    private String splitPk;
    private Integer batchSize;
    private String encoding;
    private String preSql;
    private String postSql;
}
