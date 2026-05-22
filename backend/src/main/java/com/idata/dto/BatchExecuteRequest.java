package com.idata.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchExecuteRequest {
    @NotNull(message = "数据源ID不能为空")
    private Long datasourceId;

    @NotEmpty(message = "SQL 列表不能为空")
    private List<String> sqls;
}
