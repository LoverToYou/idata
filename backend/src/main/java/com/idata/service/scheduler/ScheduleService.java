package com.idata.service.scheduler;

import com.idata.dto.ScheduleRequest;
import com.idata.dto.ScheduleVO;
import com.idata.engine.workflow.DagExecutor;
import com.idata.entity.ScheduleConfig;
import com.idata.entity.WorkflowDefinition;
import com.idata.mapper.ScheduleConfigMapper;
import com.idata.mapper.WorkflowDefinitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private final ScheduleConfigMapper scheduleConfigMapper;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowSchedulerService workflowSchedulerService;
    private final DagExecutor dagExecutor;

    public ScheduleService(ScheduleConfigMapper scheduleConfigMapper,
                           WorkflowDefinitionMapper workflowDefinitionMapper,
                           WorkflowSchedulerService workflowSchedulerService,
                           DagExecutor dagExecutor) {
        this.scheduleConfigMapper = scheduleConfigMapper;
        this.workflowDefinitionMapper = workflowDefinitionMapper;
        this.workflowSchedulerService = workflowSchedulerService;
        this.dagExecutor = dagExecutor;
    }

    public List<ScheduleVO> listAll() {
        List<ScheduleConfig> configs = scheduleConfigMapper.selectList(null);
        List<WorkflowDefinition> workflows = workflowDefinitionMapper.selectList(null);
        Map<Long, String> workflowNameMap = workflows.stream()
                .collect(Collectors.toMap(WorkflowDefinition::getId, WorkflowDefinition::getName));
        return configs.stream()
                .map(c -> toVO(c, workflowNameMap.get(c.getWorkflowId())))
                .collect(Collectors.toList());
    }

    public List<ScheduleVO> listByWorkflowId(Long workflowId) {
        List<ScheduleConfig> configs = scheduleConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduleConfig>()
                        .eq(ScheduleConfig::getWorkflowId, workflowId));
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(workflowId);
        String workflowName = workflow != null ? workflow.getName() : null;
        return configs.stream()
                .map(c -> toVO(c, workflowName))
                .collect(Collectors.toList());
    }

    public ScheduleVO create(ScheduleRequest req) {
        // Validate workflow exists
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(req.getWorkflowId());
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + req.getWorkflowId());
        }

        // Validate cron expression
        if (!org.quartz.CronExpression.isValidExpression(req.getCronExpression())) {
            throw new IllegalArgumentException("无效的Cron表达式: " + req.getCronExpression());
        }

        ScheduleConfig config = new ScheduleConfig();
        config.setWorkflowId(req.getWorkflowId());
        config.setCronExpression(req.getCronExpression());
        config.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        config.setRetryCount(req.getRetryCount() != null ? req.getRetryCount() : 0);
        config.setRetryIntervalSeconds(req.getRetryIntervalSeconds() != null ? req.getRetryIntervalSeconds() : 60);
        scheduleConfigMapper.insert(config);
        workflowSchedulerService.register(config);
        return toVO(config, workflow.getName());
    }

    public ScheduleVO update(ScheduleRequest req) {
        if (req.getId() == null) {
            throw new IllegalArgumentException("调度配置ID不能为空");
        }

        ScheduleConfig config = scheduleConfigMapper.selectById(req.getId());
        if (config == null) {
            throw new IllegalArgumentException("调度配置不存在: " + req.getId());
        }

        // Validate cron expression if provided
        if (req.getCronExpression() != null && !req.getCronExpression().isEmpty()) {
            if (!org.quartz.CronExpression.isValidExpression(req.getCronExpression())) {
                throw new IllegalArgumentException("无效的Cron表达式: " + req.getCronExpression());
            }
            config.setCronExpression(req.getCronExpression());
        }

        if (req.getWorkflowId() != null) {
            WorkflowDefinition workflow = workflowDefinitionMapper.selectById(req.getWorkflowId());
            if (workflow == null) {
                throw new IllegalArgumentException("工作流不存在: " + req.getWorkflowId());
            }
            config.setWorkflowId(req.getWorkflowId());
        }

        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }

        if (req.getRetryCount() != null) {
            config.setRetryCount(req.getRetryCount());
        }

        if (req.getRetryIntervalSeconds() != null) {
            config.setRetryIntervalSeconds(req.getRetryIntervalSeconds());
        }

        scheduleConfigMapper.updateById(config);

        ScheduleConfig updated = scheduleConfigMapper.selectById(req.getId());
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(updated.getWorkflowId());
        workflowSchedulerService.reschedule(updated);
        return toVO(updated, workflow != null ? workflow.getName() : null);
    }

    public void delete(Long id) {
        if (scheduleConfigMapper.selectById(id) == null) {
            throw new IllegalArgumentException("调度配置不存在: " + id);
        }
        workflowSchedulerService.remove(id);
        scheduleConfigMapper.deleteById(id);
    }

    public void toggleEnabled(Long id, boolean enabled) {
        ScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("调度配置不存在: " + id);
        }
        config.setEnabled(enabled);
        scheduleConfigMapper.updateById(config);
        if (enabled) {
            workflowSchedulerService.resume(id);
        } else {
            workflowSchedulerService.pause(id);
        }
    }

    /**
     * Trigger a schedule's workflow once immediately.
     *
     * @return the created WorkflowInstance ID
     */
    public Long triggerSchedule(Long id) {
        ScheduleConfig config = scheduleConfigMapper.selectById(id);
        if (config == null) {
            throw new IllegalArgumentException("调度配置不存在: " + id);
        }
        log.info("Manually triggering schedule {} -> workflow {} (cron: {})",
                id, config.getWorkflowId(), config.getCronExpression());
        return dagExecutor.execute(config.getWorkflowId(), "CRON");
    }

    private ScheduleVO toVO(ScheduleConfig config, String workflowName) {
        ScheduleVO vo = new ScheduleVO();
        vo.setId(config.getId());
        vo.setWorkflowId(config.getWorkflowId());
        vo.setWorkflowName(workflowName);
        vo.setCronExpression(config.getCronExpression());
        vo.setEnabled(config.getEnabled());
        vo.setRetryCount(config.getRetryCount());
        vo.setRetryIntervalSeconds(config.getRetryIntervalSeconds());
        vo.setCreatedAt(config.getCreatedAt());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
