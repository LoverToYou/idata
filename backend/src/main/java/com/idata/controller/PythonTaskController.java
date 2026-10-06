package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.*;
import com.idata.service.python.PythonTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/python-task")
public class PythonTaskController {

    private final PythonTaskService pythonTaskService;

    public PythonTaskController(PythonTaskService pythonTaskService) {
        this.pythonTaskService = pythonTaskService;
    }

    @GetMapping("/list")
    public Result<List<PythonScriptVO>> list(@RequestParam(required = false) String keyword) {
        return Result.success(pythonTaskService.listAll(keyword));
    }

    @GetMapping("/page")
    public Result<PageResult<PythonScriptVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(pythonTaskService.listPage(keyword, page, pageSize));
    }

    @GetMapping("/{id:\\d+}")
    public Result<PythonScriptVO> getById(@PathVariable Long id) {
        return Result.success(pythonTaskService.getById(id));
    }

    @PostMapping("/create")
    public Result<PythonScriptVO> create(@Valid @RequestBody PythonScriptRequest request) {
        return Result.success(pythonTaskService.create(request));
    }

    @PutMapping("/update")
    public Result<PythonScriptVO> update(@Valid @RequestBody PythonScriptRequest request) {
        return Result.success(pythonTaskService.update(request));
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable Long id) {
        pythonTaskService.delete(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        pythonTaskService.deleteBatch(ids);
        return Result.success();
    }

    @PostMapping("/{id}/run")
    public Result<Long> run(@PathVariable Long id, @RequestBody(required = false) RunRequest request) {
        String params = request != null ? request.getParams() : null;
        return Result.success(pythonTaskService.startRun(id, params, "MANUAL"));
    }

    @PostMapping("/{id}/publish")
    public Result<PythonScriptVO> publish(@PathVariable Long id) {
        return Result.success(pythonTaskService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public Result<PythonScriptVO> unpublish(@PathVariable Long id) {
        return Result.success(pythonTaskService.unpublish(id));
    }

    @GetMapping("/runs")
    public Result<PageResult<PythonRunVO>> runs(
            @RequestParam(required = false) Long scriptId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(pythonTaskService.listRuns(scriptId, page, pageSize));
    }

    @GetMapping("/runs/{runId}")
    public Result<PythonRunVO> getRun(@PathVariable Long runId) {
        return Result.success(pythonTaskService.getRun(runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public Result<Void> cancelRun(@PathVariable Long runId) {
        pythonTaskService.cancelRun(runId);
        return Result.success();
    }
}
