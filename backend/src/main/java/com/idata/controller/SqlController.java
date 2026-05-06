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

        // Basic SQL formatting (uppercase keywords, line breaks)
        String formatted = sql
                .replaceAll("(?i)\\bselect\\b", "SELECT")
                .replaceAll("(?i)\\bfrom\\b", "\nFROM")
                .replaceAll("(?i)\\bwhere\\b", "\nWHERE")
                .replaceAll("(?i)\\band\\b", "\n  AND")
                .replaceAll("(?i)\\bor\\b", "\n  OR")
                .replaceAll("(?i)\\bjoin\\b", "\nJOIN")
                .replaceAll("(?i)\\bleft\\s+join\\b", "\nLEFT JOIN")
                .replaceAll("(?i)\\bright\\s+join\\b", "\nRIGHT JOIN")
                .replaceAll("(?i)\\binner\\s+join\\b", "\nINNER JOIN")
                .replaceAll("(?i)\\bon\\b", "\n  ON")
                .replaceAll("(?i)\\border\\s+by\\b", "\nORDER BY")
                .replaceAll("(?i)\\bgroup\\s+by\\b", "\nGROUP BY")
                .replaceAll("(?i)\\bhaving\\b", "\nHAVING")
                .replaceAll("(?i)\\blimit\\b", "\nLIMIT")
                .replaceAll("(?i)\\binsert\\s+into\\b", "INSERT INTO")
                .replaceAll("(?i)\\bvalues\\b", "\nVALUES")
                .replaceAll("(?i)\\bunion\\b", "\nUNION")
                .trim();

        Map<String, String> result = new HashMap<>();
        result.put("formatted", formatted);

        return Result.success(result);
    }
}
