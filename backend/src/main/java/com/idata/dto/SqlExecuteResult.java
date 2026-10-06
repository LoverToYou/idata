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
    /** 结果集是否因超过行数上限被截断（前端据此提示「仅展示前 N 条」） */
    private boolean truncated;
}
