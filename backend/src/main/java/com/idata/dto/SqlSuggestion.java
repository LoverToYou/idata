package com.idata.dto;

import lombok.Data;

@Data
public class SqlSuggestion {
    private String type; // WARNING / ERROR / INFO
    private String title;
    private String detail;
    private String suggestion;
}
