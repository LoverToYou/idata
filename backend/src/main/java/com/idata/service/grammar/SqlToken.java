package com.idata.service.grammar;

/**
 * SQL 分词结果中的单个 Token。
 */
public class SqlToken {

    public enum Type {
        KEYWORD,
        IDENTIFIER,
        STRING,
        NUMBER,
        OPERATOR,
        WHITESPACE,
        COMMENT,
        UNKNOWN
    }

    private final Type type;
    private final String value;
    private final String upperValue;
    private final int start;
    private final int end;

    public SqlToken(Type type, String value, int start, int end) {
        this.type = type;
        this.value = value;
        this.upperValue = value.toUpperCase();
        this.start = start;
        this.end = end;
    }

    public Type getType() { return type; }
    public String getValue() { return value; }
    public String getUpperValue() { return upperValue; }
    public int getStart() { return start; }
    public int getEnd() { return end; }

    @Override
    public String toString() {
        return "SqlToken{" + type + "='" + value + "'@" + start + "-" + end + "}";
    }
}
