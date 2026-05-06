package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.WorkflowRequest;
import com.idata.dto.WorkflowVO;
import com.idata.entity.NodeExecutionLog;
import com.idata.entity.WorkflowInstance;
import com.idata.service.workflow.WorkflowInstanceService;
import com.idata.service.workflow.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflow")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowInstanceService workflowInstanceService;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowInstanceService workflowInstanceService) {
        this.workflowService = workflowService;
        this.workflowInstanceService = workflowInstanceService;
    }

    @GetMapping("/list")
    public Result<List<WorkflowVO>> list() {
        return Result.success(workflowService.listAll());
    }

    @GetMapping("/{id}")
    public Result<WorkflowVO> getById(@PathVariable Long id) {
        return Result.success(workflowService.getById(id));
    }

    @PostMapping("/create")
    public Result<WorkflowVO> create(@Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.create(request));
    }

    @PutMapping("/update")
    public Result<WorkflowVO> update(@Valid @RequestBody WorkflowRequest request) {
        return Result.success(workflowService.update(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.success();
    }

    @PostMapping("/{id}/publish")
    public Result<WorkflowVO> publish(@PathVariable Long id) {
        return Result.success(workflowService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public Result<WorkflowVO> unpublish(@PathVariable Long id) {
        return Result.success(workflowService.unpublish(id));
    }

    // --- Workflow instance endpoints ---

    @GetMapping("/{id}/instances")
    public Result<List<WorkflowInstance>> listInstances(@PathVariable Long id) {
        return Result.success(workflowInstanceService.listByWorkflowId(id));
    }

    @GetMapping("/instance/{instanceId}/logs")
    public Result<List<NodeExecutionLog>> getNodeLogs(@PathVariable Long instanceId) {
        return Result.success(workflowInstanceService.getNodeLogs(instanceId));
    }
}
