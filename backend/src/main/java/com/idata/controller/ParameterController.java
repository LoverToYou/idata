package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.ParameterRequest;
import com.idata.dto.ParameterVO;
import com.idata.service.sql.ParameterService;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/parameter")
public class ParameterController {

    private final ParameterService parameterService;
    private final JdbcTemplate jdbcTemplate;

    public ParameterController(ParameterService parameterService, DataSource dataSource) {
        this.parameterService = parameterService;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @GetMapping("/list")
    public Result<List<ParameterVO>> list() {
        return Result.success(parameterService.listAll());
    }

    @GetMapping("/{id}")
    public Result<ParameterVO> getById(@PathVariable Long id) {
        return Result.success(parameterService.getById(id));
    }

    @PostMapping("/create")
    public Result<ParameterVO> create(@Valid @RequestBody ParameterRequest req) {
        return Result.success(parameterService.create(req));
    }

    @PutMapping("/update")
    public Result<ParameterVO> update(@Valid @RequestBody ParameterRequest req) {
        return Result.success(parameterService.update(req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        parameterService.delete(id);
        return Result.success();
    }

    @PostMapping("/resolve")
    public Result<Map<String, Object>> resolve(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null) {
            return Result.error(400, "SQL 不能为空");
        }
        return Result.success(parameterService.resolveAndGetMap(sql));
    }

    @PostMapping("/execute-sql")
    public Result<Map<String, Object>> executeSql(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.isBlank()) {
            return Result.error(400, "SQL 不能为空");
        }

        long start = System.currentTimeMillis();
        try {
            String upper = sql.trim().toUpperCase();
            boolean isQuery = upper.startsWith("SELECT") || upper.startsWith("SHOW") || upper.startsWith("DESC") || upper.startsWith("EXPLAIN") || upper.startsWith("WITH");

            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            int affectedRows = -1;

            if (isQuery) {
                List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
                rows.addAll(result);
                if (!result.isEmpty()) {
                    columns.addAll(result.get(0).keySet());
                }
            } else {
                affectedRows = jdbcTemplate.update(sql);
            }

            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> data = new HashMap<>();
            data.put("columns", columns);
            data.put("rows", rows);
            data.put("affectedRows", affectedRows);
            data.put("elapsedMs", elapsed);
            data.put("success", true);
            return Result.success(data);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("errorMessage", e.getMessage());
            data.put("elapsedMs", elapsed);
            return Result.success(data);
        }
    }
}
