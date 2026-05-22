package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.SqlGrammarContext;
import com.idata.dto.SqlGrammarRequest;
import com.idata.service.grammar.SqlGrammarService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * SQL 语法分析控制器。
 * 用于前端自动补全的上下文感知。
 */
@RestController
@RequestMapping("/sql/grammar")
public class SqlGrammarController {

    private final SqlGrammarService sqlGrammarService;

    public SqlGrammarController(SqlGrammarService sqlGrammarService) {
        this.sqlGrammarService = sqlGrammarService;
    }

    /**
     * 检测光标位置的 SQL 语法上下文。
     */
    @PostMapping("/context")
    public Result<SqlGrammarContext> detectContext(@Valid @RequestBody SqlGrammarRequest request) {
        SqlGrammarContext context = sqlGrammarService.detectContext(
                request.getSql(), request.getCursorPosition());
        return Result.success(context);
    }
}
