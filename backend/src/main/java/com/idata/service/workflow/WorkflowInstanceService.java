package com.idata.service.workflow;

import com.idata.entity.NodeExecutionLog;
import com.idata.entity.WorkflowInstance;
import com.idata.mapper.NodeExecutionLogMapper;
import com.idata.mapper.WorkflowInstanceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkflowInstanceService {

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final NodeExecutionLogMapper nodeExecutionLogMapper;

    public WorkflowInstanceService(WorkflowInstanceMapper workflowInstanceMapper,
                                   NodeExecutionLogMapper nodeExecutionLogMapper) {
        this.workflowInstanceMapper = workflowInstanceMapper;
        this.nodeExecutionLogMapper = nodeExecutionLogMapper;
    }

    public WorkflowInstance createInstance(Long workflowId, String triggeredBy) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowId(workflowId);
        instance.setStatus("RUNNING");
        instance.setStartedAt(LocalDateTime.now());
        instance.setTriggeredBy(triggeredBy);
        workflowInstanceMapper.insert(instance);
        return instance;
    }

    public void completeInstance(Long instanceId, String status, String errorMessage) {
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("工作流实例不存在: " + instanceId);
        }
        instance.setStatus(status);
        instance.setFinishedAt(LocalDateTime.now());
        instance.setErrorMessage(errorMessage);
        workflowInstanceMapper.updateById(instance);
    }

    public List<WorkflowInstance> listByWorkflowId(Long workflowId) {
        return workflowInstanceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowInstance::getCreatedAt));
    }

    public List<NodeExecutionLog> getNodeLogs(Long instanceId) {
        return nodeExecutionLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NodeExecutionLog>()
                        .eq(NodeExecutionLog::getInstanceId, instanceId)
                        .orderByAsc(NodeExecutionLog::getCreatedAt));
    }
}
