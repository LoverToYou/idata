package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.*;
import com.idata.service.sql.SqlExecutorService;
import com.idata.service.sql.SqlOptimizerService;
import com.idata.service.sql.SqlParserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sql")
public class SqlController {

    private final SqlExecutorService sqlExecutorService;
    private final SqlParserService sqlParserService;
    private final SqlOptimizerService sqlOptimizerService;

    public SqlController(SqlExecutorService sqlExecutorService,
                         SqlParserService sqlParserService,
                         SqlOptimizerService sqlOptimizerService) {
        this.sqlExecutorService = sqlExecutorService;
        this.sqlParserService = sqlParserService;
        this.sqlOptimizerService = sqlOptimizerService;
    }

    @PostMapping("/execute")
    public Result<SqlExecuteResult> execute(@Valid @RequestBody SqlExecuteRequest request) {
        SqlExecuteResult result = sqlExecutorService.execute(request.getDatasourceId(), request.getSql());
        if (result.getErrorMessage() != null) {
            return Result.error(500, result.getErrorMessage());
        }
        return Result.success(result);
    }

    @PostMapping("/explain")
    public Result<ExplainPlanResult> explain(@Valid @RequestBody SqlExecuteRequest request) {
        ExplainPlanResult result = sqlExecutorService.explain(request.getDatasourceId(), request.getSql());
        return Result.success(result);
    }

    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyze(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return Result.error(400, "SQL 不能为空");
        }

        SqlAnalysisResult analysis = sqlParserService.analyze(sql);

        Map<String, Object> result = new HashMap<>();
        result.put("analysis", analysis);

        return Result.success(result);
    }

    @PostMapping("/full-analyze")
    public Result<Map<String, Object>> fullAnalyze(@Valid @RequestBody SqlExecuteRequest request) {
        String sql = request.getSql();
        Long datasourceId = request.getDatasourceId();

        // 1. Parse and analyze SQL
        SqlAnalysisResult analysis = sqlParserService.analyze(sql);

        // 2. Get EXPLAIN plan
        ExplainPlanResult plan = sqlExecutorService.explain(datasourceId, sql);

        // 3. Generate optimization suggestions from plan
        List<SqlSuggestion> planSuggestions = sqlOptimizerService.analyzePlan(plan);

        // Merge parse suggestions + plan suggestions
        List<SqlSuggestion> allSuggestions = analysis.getSuggestions();
        if (allSuggestions == null) {
            allSuggestions = planSuggestions;
        } else {
            allSuggestions.addAll(planSuggestions);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("analysis", analysis);
        result.put("plan", plan);
        result.put("suggestions", allSuggestions);

        return Result.success(result);
    }

    @PostMapping("/format")
    public Result<Map<String, String>> format(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return Result.error(400, "SQL 不能为空");
        }

        String formatted = formatSqlInternal(sql);

        Map<String, String> result = new HashMap<>();
        result.put("formatted", formatted);

        return Result.success(result);
    }

    /**
     * SQL 格式化（阿里云 DataWorks 编码规范）。
     * - 关键字大写
     * - SELECT 列一行一个，逗号前置
     * - 子句换行缩进
     */
    private String formatSqlInternal(String sql) {
        String s = sql.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");

        // 1. 归一化空白（保留换行）
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n\\s*", "\n");
        s = s.replaceAll("\\s+", " ");

        // 2. 关键字大写
        String[][] keywords = {
                {"(?i)\\bselect\\b", "SELECT"},
                {"(?i)\\bdistinct\\b", "DISTINCT"},
                {"(?i)\\bfrom\\b", "FROM"},
                {"(?i)\\bwhere\\b", "WHERE"},
                {"(?i)\\band\\b", "AND"},
                {"(?i)\\bor\\b", "OR"},
                {"(?i)\\bnot\\b", "NOT"},
                {"(?i)\\bin\\b", "IN"},
                {"(?i)\\blike\\b", "LIKE"},
                {"(?i)\\brlike\\b", "RLIKE"},
                {"(?i)\\bregexp\\b", "REGEXP"},
                {"(?i)\\bbetween\\b", "BETWEEN"},
                {"(?i)\\bexists\\b", "EXISTS"},
                {"(?i)\\bjoin\\b", "JOIN"},
                {"(?i)\\bleft\\s+join\\b", "LEFT JOIN"},
                {"(?i)\\bright\\s+join\\b", "RIGHT JOIN"},
                {"(?i)\\binner\\s+join\\b", "INNER JOIN"},
                {"(?i)\\bcross\\s+join\\b", "CROSS JOIN"},
                {"(?i)\\bnatural\\s+join\\b", "NATURAL JOIN"},
                {"(?i)\\bon\\b", "ON"},
                {"(?i)\\border\\s+by\\b", "ORDER BY"},
                {"(?i)\\bgroup\\s+by\\b", "GROUP BY"},
                {"(?i)\\bhaving\\b", "HAVING"},
                {"(?i)\\blimit\\b", "LIMIT"},
                {"(?i)\\boffset\\b", "OFFSET"},
                {"(?i)\\binsert\\s+into\\b", "INSERT INTO"},
                {"(?i)\\bvalues\\b", "VALUES"},
                {"(?i)\\bunion\\b", "UNION"},
                {"(?i)\\bexcept\\b", "EXCEPT"},
                {"(?i)\\bintersect\\b", "INTERSECT"},
                {"(?i)\\bcreate\\b", "CREATE"},
                {"(?i)\\btable\\b", "TABLE"},
                {"(?i)\\bdelete\\b", "DELETE"},
                {"(?i)\\bupdate\\b", "UPDATE"},
                {"(?i)\\bset\\b", "SET"},
                {"(?i)\\bnull\\b", "NULL"},
                {"(?i)\\bis\\b", "IS"},
                {"(?i)\\bas\\b", "AS"},
                {"(?i)\\basc\\b", "ASC"},
                {"(?i)\\bdesc\\b", "DESC"},
                {"(?i)\\bcase\\b", "CASE"},
                {"(?i)\\bwhen\\b", "WHEN"},
                {"(?i)\\bthen\\b", "THEN"},
                {"(?i)\\belse\\b", "ELSE"},
                {"(?i)\\bend\\b", "END"},
                {"(?i)\\bwith\\b", "WITH"},
                {"(?i)\\binto\\b", "INTO"},
        };
        for (String[] kw : keywords) {
            s = s.replaceAll(kw[0], kw[1]);
        }

        // 3. 子句换行
        s = s.replaceAll("\\bFROM\\b", "\nFROM");
        s = s.replaceAll("\\bWHERE\\b", "\nWHERE");
        s = s.replaceAll("\\bGROUP BY\\b", "\nGROUP BY");
        s = s.replaceAll("\\bORDER BY\\b", "\nORDER BY");
        s = s.replaceAll("\\bHAVING\\b", "\nHAVING");
        s = s.replaceAll("\\bLIMIT\\b", "\nLIMIT");
        s = s.replaceAll("\\bUNION\\b", "\nUNION");
        s = s.replaceAll("\\bEXCEPT\\b", "\nEXCEPT");
        s = s.replaceAll("\\bINTERSECT\\b", "\nINTERSECT");

        // JOIN 单独处理（避免匹配到单词内含 join）
        s = s.replaceAll("(?<!\\w)JOIN\\b", "\nJOIN");
        s = s.replaceAll("LEFT JOIN", "\nLEFT JOIN");
        s = s.replaceAll("RIGHT JOIN", "\nRIGHT JOIN");
        s = s.replaceAll("INNER JOIN", "\nINNER JOIN");
        s = s.replaceAll("CROSS JOIN", "\nCROSS JOIN");
        s = s.replaceAll("NATURAL JOIN", "\nNATURAL JOIN");

        // ON、AND、OR 缩进
        s = s.replaceAll("\\bON\\b", "\n  ON");
        s = s.replaceAll("\\bAND\\b", "\n  AND");
        s = s.replaceAll("\\bOR\\b", "\n  OR");

        // VALUES 换行
        s = s.replaceAll("\\bVALUES\\b", "\nVALUES");

        // INSERT INTO 保持在一行
        s = s.replaceAll("INSERT\\s+INTO", "INSERT INTO");

        // 4. SELECT 列分行（逗号前置风格）
        s = splitSelectColumns(s);

        // 5. 清理多余空行和前后空格
        s = s.replaceAll("\\n{3,}", "\n\n").trim();

        return s;
    }

    /**
     * 将 SELECT 子句中的列按逗号拆分为多行，逗号前置。
     */
    private String splitSelectColumns(String sql) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = sql.length();

        while (i < len) {
            // 跳过字符串和注释
            if (i < len && (sql.charAt(i) == '\'' || sql.charAt(i) == '"')) {
                char quote = sql.charAt(i);
                int end = sql.indexOf(quote, i + 1);
                if (end < 0) end = len;
                result.append(sql, i, end + 1);
                i = end + 1;
                continue;
            }
            if (i < len - 1 && sql.charAt(i) == '-' && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                if (end < 0) end = len;
                result.append(sql, i, end);
                i = end;
                continue;
            }
            if (i < len - 1 && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end < 0) end = len - 2;
                result.append(sql, i, end + 2);
                i = end + 2;
                continue;
            }

            // 查找 SELECT ... FROM 模式
            if (i + 6 < len && "SELECT".equalsIgnoreCase(sql.substring(i, i + 6).trim())
                    && (i == 0 || !Character.isLetter(sql.charAt(i - 1)))) {
                int selStart = i;
                int selEnd = sql.indexOf('\n', i + 6);
                if (selEnd < 0) selEnd = len;

                // 查找 FROM (注意跳过子查询中的 FROM)
                int fromPos = findClauseEnd(sql, selEnd, "FROM");
                if (fromPos < 0) {
                    result.append(sql.charAt(i));
                    i++;
                    continue;
                }

                // 检查 SELECT 和 FROM 是否在同一行
                int fromLineStart = fromPos;
                while (fromLineStart > selEnd && sql.charAt(fromLineStart) != '\n') fromLineStart--;
                if (fromLineStart == selEnd) {
                    // SELECT 和 FROM 之间没有换行，是在同一行
                    // 需要拆分
                    String cols = sql.substring(selEnd, fromPos).trim();
                    result.append("SELECT\n  ");
                    result.append(cols.replaceAll("\\s*,\\s*", "\n, "));
                    i = fromPos;
                } else {
                    result.append(sql.charAt(i));
                    i++;
                }
                continue;
            }

            result.append(sql.charAt(i));
            i++;
        }

        return result.toString();
    }

    /**
     * 从位置 start 开始查找关键字 clause 的首次出现（跳过括号内的内容）。
     */
    private int findClauseEnd(String sql, int start, String clause) {
        int depth = 0;
        for (int i = start; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (depth == 0 && i + clause.length() <= sql.length()
                    && clause.equalsIgnoreCase(sql.substring(i, i + clause.length()).trim())
                    && (i + clause.length() >= sql.length() || !Character.isLetter(sql.charAt(i + clause.length())))) {
                // 确认是独立的单词
                if (i > 0 && Character.isLetter(sql.charAt(i - 1))) continue;
                return i;
            }
        }
        return -1;
    }
}
