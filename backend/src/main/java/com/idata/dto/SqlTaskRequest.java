package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SqlTaskRequest {
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String name;

    private String description;

    private Long datasourceId;

    private String sqlContent;

    /** 执行引擎：HIVE / SPARK（空=跟随数据源默认） */
    private String engine;
}
