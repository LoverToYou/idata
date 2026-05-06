package com.idata.dto;

import lombok.Data;

import java.util.List;

@Data
public class SqlAnalysisResult {
    private boolean valid;
    private String errorMessage;
    private List<String> tables;
    private List<String> columns;
    private String queryType;
    private List<String> joinTypes;
    private List<SqlSuggestion> suggestions;
}
