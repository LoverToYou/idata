package com.idata.controller;

import com.idata.common.Result;
import com.idata.dto.InstanceVO;
import com.idata.engine.workflow.DagExecutor;
import com.idata.entity.NodeExecutionLog;
import com.idata.entity.WorkflowDefinition;
import com.idata.entity.WorkflowInstance;
import com.idata.mapper.NodeExecutionLogMapper;
import com.idata.mapper.WorkflowDefinitionMapper;
import com.idata.mapper.WorkflowInstanceMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/monitor")
public class MonitorController {

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final NodeExecutionLogMapper nodeExecutionLogMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final DagExecutor dagExecutor;

    public MonitorController(WorkflowInstanceMapper workflowInstanceMapper,
                             NodeExecutionLogMapper nodeExecutionLogMapper,
                             WorkflowDefinitionMapper workflowDefinitionMapper,
                             DagExecutor dagExecutor) {
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.nodeExecutionLogMapper = nodeExecutionLogMapper;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.dagExecutor = dagExecutor;
    }

    @GetMapping("/instances")
    public Result<List<InstanceVO>> listInstances(@RequestParam(required = false) Long workflowId) {
        List<WorkflowInstance> instances;
        if (workflowId != null) {
            instances = workflowInstanceMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowInstance>()
                            .eq(WorkflowInstance::getWorkflowId, workflowId)
                            .orderByDesc(WorkflowInstance::getCreatedAt));
        } else {
            instances = workflowInstanceMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowInstance>()
                            .orderByDesc(WorkflowInstance::getCreatedAt));
        }

        List<WorkflowDefinition> workflows = workflowDefinitionMapper.selectList(null);
        Map<Long, String> workflowNameMap = workflows.stream()
                .collect(Collectors.toMap(WorkflowDefinition::getId, WorkflowDefinition::getName));

        List<InstanceVO> voList = instances.stream()
                .map(inst -> toInstanceVO(inst, workflowNameMap.get(inst.getWorkflowId())))
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/instances/{id}")
    public Result<InstanceVO> getInstance(@PathVariable Long id) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(id);
        if (instance == null) {
            return Result.error("工作流实例不存在: " + id);
        }
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(instance.getWorkflowId());
        String workflowName = workflow != null ? workflow.getName() : null;
        return Result.success(toInstanceVO(instance, workflowName));
    }

    @GetMapping("/instances/{id}/nodes")
    public Result<List<NodeExecutionLog>> getNodeLogs(@PathVariable Long id) {
        List<NodeExecutionLog> logs = nodeExecutionLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NodeExecutionLog>()
                        .eq(NodeExecutionLog::getInstanceId, id)
                        .orderByAsc(NodeExecutionLog::getCreatedAt));
        return Result.success(logs);
    }

    @PostMapping("/workflow/{workflowId}/run")
    public Result<InstanceVO> runWorkflow(@PathVariable Long workflowId) {
        Long instanceId = dagExecutor.execute(workflowId, "MANUAL");
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(instance.getWorkflowId());
        String workflowName = workflow != null ? workflow.getName() : null;
        return Result.success(toInstanceVO(instance, workflowName));
    }

    private InstanceVO toInstanceVO(WorkflowInstance instance, String workflowName) {
        InstanceVO vo = new InstanceVO();
        vo.setId(instance.getId());
        vo.setWorkflowId(instance.getWorkflowId());
        vo.setWorkflowName(workflowName);
        vo.setStatus(instance.getStatus());
        vo.setStartedAt(instance.getStartedAt());
        vo.setFinishedAt(instance.getFinishedAt());
        vo.setTriggeredBy(instance.getTriggeredBy());
        vo.setErrorMessage(instance.getErrorMessage());
        vo.setCreatedAt(instance.getCreatedAt());
        return vo;
    }
}
