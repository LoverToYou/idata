package com.idata.service.sql;

import com.idata.dto.SqlAnalysisResult;
import com.idata.dto.SqlSuggestion;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SqlParserService {

    public SqlAnalysisResult analyze(String sql) {
        SqlAnalysisResult result = new SqlAnalysisResult();

        try {
            SqlParser parser = SqlParser.create(sql, SqlParser.config().withLex(Lex.MYSQL));
            SqlNode node = parser.parseQuery();

            result.setValid(true);

            if (node instanceof SqlSelect select) {
                result.setQueryType("SELECT");

                // Extract tables
                Set<String> tables = new HashSet<>();
                extractTables(select.getFrom(), tables);
                result.setTables(new ArrayList<>(tables));

                // Extract columns from SELECT
                List<String> columns = new ArrayList<>();
                if (select.getSelectList() != null) {
                    for (SqlNode selNode : select.getSelectList()) {
                        if (selNode instanceof SqlBasicCall call) {
                            columns.add(call.toString());
                        } else {
                            columns.add(selNode.toString());
                        }
                    }
                }
                result.setColumns(columns);

                // Detect JOINs
                List<String> joinTypes = new ArrayList<>();
                detectJoins(select.getFrom(), joinTypes);
                result.setJoinTypes(joinTypes);

                // Generate suggestions
                List<SqlSuggestion> suggestions = generateSuggestions(select, columns, tables);
                result.setSuggestions(suggestions);
            } else if (node instanceof SqlInsert) {
                result.setQueryType("INSERT");
            } else if (node instanceof SqlUpdate) {
                result.setQueryType("UPDATE");
            } else if (node instanceof SqlDelete) {
                result.setQueryType("DELETE");
            } else {
                result.setQueryType("OTHER");
            }

        } catch (SqlParseException e) {
            result.setValid(false);
            result.setErrorMessage("SQL 解析错误: " + e.getMessage());
        } catch (Exception e) {
            result.setValid(false);
            result.setErrorMessage("解析异常: " + e.getMessage());
        }

        return result;
    }

    private List<SqlSuggestion> generateSuggestions(SqlSelect select, List<String> columns,
                                                     Set<String> tables) {
        List<SqlSuggestion> suggestions = new ArrayList<>();

        // Check for SELECT *
        if (columns.size() == 1 && columns.get(0).equals("*")) {
            SqlSuggestion s = new SqlSuggestion();
            s.setType("WARNING");
            s.setTitle("避免使用 SELECT *");
            s.setDetail("当前查询使用了 SELECT *，会读取所有列。");
            s.setSuggestion("请明确列出需要的列名，减少 IO 和网络传输。");
            suggestions.add(s);
        }

        // Check for missing WHERE clause
        if (select.getWhere() == null && tables.size() > 0) {
            SqlSuggestion s = new SqlSuggestion();
            s.setType("WARNING");
            s.setTitle("缺少 WHERE 条件");
            s.setDetail("查询没有 WHERE 条件，将返回全表数据。");
            s.setSuggestion("请添加 WHERE 条件过滤数据，或确认是否需要全表扫描。");
            suggestions.add(s);
        }

        // Check for Cartesian JOIN (no join condition)
        if (select.getFrom() instanceof SqlJoin join && join.getCondition() == null) {
            SqlSuggestion s = new SqlSuggestion();
            s.setType("ERROR");
            s.setTitle("笛卡尔积 JOIN");
            s.setDetail("检测到没有连接条件的 JOIN，会产生笛卡尔积。");
            s.setSuggestion("请添加 ON 条件指定表之间的连接关系。");
            suggestions.add(s);
        }

        // Check for ORDER BY without LIMIT on large queries
        if (select.getOrderList() != null && !select.getOrderList().isEmpty()
                && select.getFetch() == null && select.getOffset() == null) {
            SqlSuggestion s = new SqlSuggestion();
            s.setType("INFO");
            s.setTitle("ORDER BY 未搭配 LIMIT");
            s.setDetail("ORDER BY 会对全结果集排序，未使用 LIMIT 可能导致大量排序开销。");
            s.setSuggestion("考虑添加 LIMIT 限制返回行数。");
            suggestions.add(s);
        }

        // Suggest using WHERE instead of HAVING when possible
        if (select.getHaving() != null && select.getWhere() == null) {
            SqlSuggestion s = new SqlSuggestion();
            s.setType("INFO");
            s.setTitle("使用 HAVING 但未使用 WHERE");
            s.setDetail("HAVING 在聚合后过滤，效率低于 WHERE 在聚合前过滤。");
            s.setSuggestion("将能在 WHERE 中过滤的条件移到 WHERE 子句。");
            suggestions.add(s);
        }

        return suggestions;
    }

    private void extractTables(SqlNode node, Set<String> tables) {
        if (node instanceof SqlIdentifier identifier) {
            String name = identifier.toString();
            if (!name.isEmpty() && Character.isLetter(name.charAt(0))) {
                tables.add(name);
            }
        } else if (node instanceof SqlJoin join) {
            extractTables(join.getLeft(), tables);
            extractTables(join.getRight(), tables);
        } else if (node instanceof SqlBasicCall call) {
            if (call.getOperator() instanceof SqlSelectOperator) {
                // Subquery - extract tables from inner select
                for (SqlNode operand : call.getOperandList()) {
                    if (operand != null) {
                        extractTables(operand, tables);
                    }
                }
            } else if ("AS".equalsIgnoreCase(call.getOperator().getName())) {
                // table alias
                if (call.getOperandList().size() > 0) {
                    extractTables(call.getOperandList().get(0), tables);
                }
            }
        }
    }

    private void detectJoins(SqlNode node, List<String> joinTypes) {
        if (node instanceof SqlJoin join) {
            String joinType = join.getJoinType().toString();
            joinTypes.add(joinType);
            detectJoins(join.getLeft(), joinTypes);
            detectJoins(join.getRight(), joinTypes);
        }
    }
}
