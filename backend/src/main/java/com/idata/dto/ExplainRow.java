package com.idata.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExplainRow {
    private String id;
    private String selectType;
    private String table;
    private String partitions;
    private String type;
    private String possibleKeys;
    private String key;
    private String keyLen;
    private String ref;
    private String rows;
    private String filtered;
    private String extra;

    // For tree view
    private int depth;
    private List<ExplainRow> children;
}
