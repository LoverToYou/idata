package com.idata.service.grammar;

/**
 * SQL 语法状态枚举。
 * 表示光标在 SQL 中所处的语法位置，用于自动补全上下文判断。
 */
public enum SqlGrammarState {

    /** 语句开始位置（或分号后），期望语句起始关键字 */
    STATEMENT_START,

    /** SELECT 后的列/表达式列表 */
    SELECT_LIST,

    /** FROM 子句 */
    FROM_CLAUSE,

    /** JOIN 关键字后，期望表引用 */
    JOIN_CLAUSE,

    /** ON 条件子句 */
    ON_CLAUSE,

    /** WHERE/AND/OR 条件子句 */
    WHERE_CLAUSE,

    /** GROUP BY 子句 */
    GROUP_BY_CLAUSE,

    /** HAVING 过滤子句 */
    HAVING_CLAUSE,

    /** ORDER BY 排序子句 */
    ORDER_BY_CLAUSE,

    /** LIMIT 子句 */
    LIMIT_CLAUSE,

    /** UPDATE SET 赋值子句 */
    SET_CLAUSE,

    /** INSERT INTO 后，期望表名 */
    INTO_CLAUSE,

    /** VALUES 表达式列表 */
    VALUES_CLAUSE,

    /** CREATE/ALTER/DROP TABLE 后，期望表名 */
    TABLE_DEF,

    /** 点号 . 后，期望标识符（列名或表名） */
    AFTER_DOT,

    /** 函数参数内 */
    INSIDE_FUNCTION,

    /** 通用表达式位置 */
    EXPRESSION,

    /** 无法确定上下文 */
    UNKNOWN
}
