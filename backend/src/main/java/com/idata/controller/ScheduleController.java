package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.ScheduleRequest;
import com.idata.dto.ScheduleVO;
import com.idata.service.scheduler.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/list")
    public Result<List<ScheduleVO>> list() {
        return Result.success(scheduleService.listAll());
    }

    @GetMapping("/workflow/{workflowId}")
    public Result<List<ScheduleVO>> listByWorkflowId(@PathVariable Long workflowId) {
        return Result.success(scheduleService.listByWorkflowId(workflowId));
    }

    @PostMapping("/create")
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleRequest request) {
        return Result.success(scheduleService.create(request));
    }

    @PutMapping("/update")
    public Result<ScheduleVO> update(@Valid @RequestBody ScheduleRequest request) {
        return Result.success(scheduleService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        scheduleService.toggleEnabled(id, enabled);
        return Result.success();
    }
}
