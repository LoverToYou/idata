package com.idata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SqlExecuteRequest {
    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotBlank(message = "SQL 不能为空")
    private String sql;

    /** 编辑器会话 ID，可空；非空时同会话内的执行复用同一数据库连接。 */
    private String sessionId;

    /** 执行引擎：HIVE / SPARK（为空用数据源默认引擎） */
    private String engine;
}
