package com.idata.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SqlExecuteResult {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private int affectedRows;
    private long elapsedMs;
    private String errorMessage;
}
