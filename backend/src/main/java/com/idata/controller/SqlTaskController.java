package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.SqlTaskRequest;
import com.idata.dto.SqlTaskVO;
import com.idata.service.sql.SqlTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sql-task")
public class SqlTaskController {

    private final SqlTaskService sqlTaskService;

    public SqlTaskController(SqlTaskService sqlTaskService) {
        this.sqlTaskService = sqlTaskService;
    }

    @GetMapping("/list")
    public Result<List<SqlTaskVO>> list() {
        return Result.success(sqlTaskService.listAll());
    }

    @GetMapping("/{id}")
    public Result<SqlTaskVO> getById(@PathVariable Long id) {
        return Result.success(sqlTaskService.getById(id));
    }

    @PostMapping("/create")
    public Result<SqlTaskVO> create(@Valid @RequestBody SqlTaskRequest request) {
        return Result.success(sqlTaskService.create(request));
    }

    @PutMapping("/update")
    public Result<SqlTaskVO> update(@Valid @RequestBody SqlTaskRequest request) {
        return Result.success(sqlTaskService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sqlTaskService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<SqlTaskVO> publish(@PathVariable Long id) {
        return Result.success(sqlTaskService.publish(id));
    }
}
