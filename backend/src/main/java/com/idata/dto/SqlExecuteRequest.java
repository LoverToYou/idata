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
}
