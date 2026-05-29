package com.idata.dto;

import lombok.Data;
import java.util.List;

@Data
public class SqlKeywordVO {
    /** SQL 语句关键字（SELECT, INSERT, CREATE TABLE 等） */
    private List<String> statements;
    /** 内置函数（NOW(), CONCAT(), COUNT() 等） */
    private List<String> functions;
    /** 数据类型（INT, VARCHAR, BIGINT 等） */
    private List<String> types;
    /** 特殊子句/属性（AUTO_INCREMENT, ENGINE, ROW FORMAT 等） */
    private List<String> clauses;
}
