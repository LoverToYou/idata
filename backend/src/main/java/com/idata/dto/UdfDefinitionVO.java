package com.idata.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UdfDefinitionVO {
    private Long id;
    private String name;
    private String className;
    private String jarFileName;
    private String jarPath;
    private Long jarSize;
    private String databaseName;
    private Long datasourceId;
    private String datasourceName;
    private String functionType;
    private String description;
    private String registerStatus;
    private String registerMessage;
    private String registerSql;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
