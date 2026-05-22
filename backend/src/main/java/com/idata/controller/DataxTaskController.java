package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.DataxTaskRequest;
import com.idata.dto.DataxTaskVO;
import com.idata.service.datax.DataxTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datax-task")
public class DataxTaskController {

    private final DataxTaskService dataxTaskService;

    public DataxTaskController(DataxTaskService dataxTaskService) {
        this.dataxTaskService = dataxTaskService;
    }

    @GetMapping("/list")
    public Result<List<DataxTaskVO>> list() {
        return Result.success(dataxTaskService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DataxTaskVO> getById(@PathVariable Long id) {
        return Result.success(dataxTaskService.getById(id));
    }

    @PostMapping("/create")
    public Result<DataxTaskVO> create(@Valid @RequestBody DataxTaskRequest request) {
        return Result.success(dataxTaskService.create(request));
    }

    @PutMapping("/update")
    public Result<DataxTaskVO> update(@Valid @RequestBody DataxTaskRequest request) {
        return Result.success(dataxTaskService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dataxTaskService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<DataxTaskVO> publish(@PathVariable Long id) {
        return Result.success(dataxTaskService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public Result<DataxTaskVO> unpublish(@PathVariable Long id) {
        return Result.success(dataxTaskService.unpublish(id));
    }

    @GetMapping("/{id}/datax-json")
    public Result<Map<String, String>> getDataxJson(@PathVariable Long id) {
        String json = dataxTaskService.generateDataxJson(id);
        return Result.success(Map.of("dataxJson", json));
    }
}
