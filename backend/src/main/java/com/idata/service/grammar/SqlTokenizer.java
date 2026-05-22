package com.idata.service.grammar;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 分词器。
 * 将 SQL 文本拆分为 Token 列表，支持标识符（含反引号/双引号引用）、
 * 字符串、数字、操作符、注释和空白。
 */
public class SqlTokenizer {

    /**
     * 对 SQL 文本前 len 个字符进行分词。
     *
     * @param sql 完整 SQL 文本
     * @param len 只处理前 len 个字符（通常为 cursorPosition）
     * @return Token 列表
     */
    public static List<SqlToken> tokenize(String sql, int len) {
        List<SqlToken> tokens = new ArrayList<>();
        if (sql == null || sql.isEmpty() || len <= 0) return tokens;

        String text = len < sql.length() ? sql.substring(0, len) : sql;
        int i = 0;
        int n = text.length();

        while (i < n) {
            char c = text.charAt(i);

            // Whitespace
            if (Character.isWhitespace(c)) {
                int start = i;
                while (i < n && Character.isWhitespace(text.charAt(i))) i++;
                tokens.add(new SqlToken(SqlToken.Type.WHITESPACE, text.substring(start, i), start, i));
                continue;
            }

            // Line comment: --
            if (c == '-' && i + 1 < n && text.charAt(i + 1) == '-') {
                int start = i;
                i += 2;
                while (i < n && text.charAt(i) != '\n') i++;
                tokens.add(new SqlToken(SqlToken.Type.COMMENT, text.substring(start, i), start, i));
                continue;
            }

            // Block comment: /* ... */
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int start = i;
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                if (i + 1 < n) i += 2; // skip */
                else i = n; // unterminated, consume rest
                tokens.add(new SqlToken(SqlToken.Type.COMMENT, text.substring(start, i), start, i));
                continue;
            }

            // Single-quoted string: '...'
            if (c == '\'') {
                int start = i;
                i++;
                while (i < n) {
                    if (text.charAt(i) == '\'' && i + 1 < n && text.charAt(i + 1) == '\'') {
                        i += 2; // escaped quote ''
                    } else if (text.charAt(i) == '\'') {
                        i++;
                        break;
                    } else {
                        i++;
                    }
                }
                tokens.add(new SqlToken(SqlToken.Type.STRING, text.substring(start, i), start, i));
                continue;
            }

            // Backtick-quoted identifier: `...`
            if (c == '`') {
                int start = i;
                i++;
                while (i < n && text.charAt(i) != '`') i++;
                if (i < n) i++; // skip closing backtick
                tokens.add(new SqlToken(SqlToken.Type.IDENTIFIER, text.substring(start, i), start, i));
                continue;
            }

            // Double-quoted identifier: "..."
            if (c == '"') {
                int start = i;
                i++;
                while (i < n && text.charAt(i) != '"') i++;
                if (i < n) i++; // skip closing quote
                tokens.add(new SqlToken(SqlToken.Type.IDENTIFIER, text.substring(start, i), start, i));
                continue;
            }

            // Operators and punctuation (single or multi-char)
            if ("().,;=<>!+-*/%&|^~".indexOf(c) >= 0) {
                int start = i;
                // Multi-char operators: <=, >=, <>, !=, ||, &&
                if (i + 1 < n) {
                    String two = text.substring(i, i + 2);
                    if ("<=".equals(two) || ">=".equals(two) || "<>".equals(two)
                            || "!=".equals(two) || "||".equals(two) || "&&".equals(two)) {
                        tokens.add(new SqlToken(SqlToken.Type.OPERATOR, two, start, start + 2));
                        i += 2;
                        continue;
                    }
                }
                tokens.add(new SqlToken(SqlToken.Type.OPERATOR, String.valueOf(c), start, start + 1));
                i++;
                continue;
            }

            // Number: integer or decimal
            if (Character.isDigit(c)) {
                int start = i;
                while (i < n && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '.')) i++;
                tokens.add(new SqlToken(SqlToken.Type.NUMBER, text.substring(start, i), start, i));
                continue;
            }

            // Identifier or keyword: [a-zA-Z_][a-zA-Z0-9_]*
            if (Character.isLetter(c) || c == '_' || c == '@') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) i++;
                tokens.add(new SqlToken(SqlToken.Type.IDENTIFIER, text.substring(start, i), start, i));
                continue;
            }

            // Unknown character: skip
            int start = i;
            i++;
            tokens.add(new SqlToken(SqlToken.Type.UNKNOWN, text.substring(start, i), start, i));
        }

        return tokens;
    }

    /**
     * 便捷方法：分词完整字符串。
     */
    public static List<SqlToken> tokenize(String sql) {
        return tokenize(sql, sql != null ? sql.length() : 0);
    }
}
