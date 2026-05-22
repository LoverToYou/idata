package com.idata.dto;

import com.idata.service.grammar.SqlGrammarState;
import lombok.Data;

import java.util.List;

/**
 * SQL 语法上下文检测结果。
 * 描述光标在 SQL 中所处的语法位置及期望的补全类型。
 */
@Data
public class SqlGrammarContext {

    /** 语法状态 */
    private SqlGrammarState state;

    /** 查询类型：SELECT, INSERT, UPDATE, DELETE, CREATE 等 */
    private String queryType;

    /** 当前上下文下有效的关键字列表 */
    private List<String> validKeywords;

    /** 是否期望表名 */
    private boolean expectsTable;

    /** 是否期望列名 */
    private boolean expectsColumn;

    /** 是否期望数据库名 */
    private boolean expectsDatabase;

    /** 是否期望函数 */
    private boolean expectsFunction;

    /** 是否期望值 */
    private boolean expectsValue;

    /** 是否期望关键字 */
    private boolean expectsKeyword;

    /** 点号前的标识符（如 "t" 来自 "t."） */
    private String dotPrefix;

    /** 点号前缀类型：TABLE, DATABASE, UNKNOWN */
    private String dotPrefixType;

    /** 是否在子查询内 */
    private boolean insideSubquery;

    /** 括号嵌套深度 */
    private int parenthesisDepth;
}
