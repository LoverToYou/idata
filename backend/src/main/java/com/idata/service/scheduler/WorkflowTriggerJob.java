package com.idata.service.scheduler;

import com.idata.engine.workflow.DagExecutor;
import com.idata.entity.ScheduleConfig;
import com.idata.entity.WorkflowInstance;
import com.idata.mapper.ScheduleConfigMapper;
import com.idata.mapper.WorkflowInstanceMapper;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that triggers a workflow via cron. One job instance per schedule config,
 * the schedule id is part of the JobKey so each schedule runs independently.
 *
 * <p>失败重试：若本次触发的工作流运行失败(实例 FAILED 或执行抛出异常)，
 * 按 schedule_config.retry_count / retry_interval_seconds 自动整跑重试，
 * 每次重试会生成新的工作流实例(可在任务监控查看)。
 */
@DisallowConcurrentExecution
public class WorkflowTriggerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerJob.class);

    @Autowired
    private DagExecutor dagExecutor;

    @Autowired
    private ScheduleConfigMapper scheduleConfigMapper;

    @Autowired
    private WorkflowInstanceMapper workflowInstanceMapper;

    @Override
    public void execute(JobExecutionContext context) {
        Long workflowId = context.getMergedJobDataMap().getLong("workflowId");
        Long scheduleConfigId = context.getMergedJobDataMap().getLong("scheduleId");
        String scheduleKey = context.getJobDetail().getKey().getName();

        // 读取当前生效的重试配置(允许运行中直接修改配置)
        int retryCount = 0;
        int retryIntervalSeconds = 60;
        ScheduleConfig config = scheduleConfigId != null
                ? scheduleConfigMapper.selectById(scheduleConfigId) : null;
        if (config != null && config.getRetryCount() != null) {
            retryCount = Math.max(0, config.getRetryCount());
        }
        if (config != null && config.getRetryIntervalSeconds() != null) {
            retryIntervalSeconds = Math.max(1, config.getRetryIntervalSeconds());
        }

        if (config == null) {
            log.warn("Schedule config {} not found, skipping retry setup", scheduleKey);
        }

        for (int attempt = 0; attempt <= retryCount; attempt++) {
            boolean isRetry = attempt > 0;
            try {
                Long instanceId = dagExecutor.execute(workflowId, "CRON");
                if (isInstanceFailed(instanceId)) {
                    if (attempt < retryCount) {
                        log.warn("Schedule {} triggered workflow {} -> instance {} FAILED (attempt {}/{}), retrying in {}s",
                                scheduleKey, workflowId, instanceId, attempt + 1, retryCount + 1, retryIntervalSeconds);
                        sleepBeforeRetry(retryIntervalSeconds);
                    } else {
                        log.error("Schedule {} triggered workflow {} -> instance {} FAILED after {} attempts, giving up",
                                scheduleKey, workflowId, instanceId, retryCount + 1);
                    }
                } else {
                    log.info("Schedule {} triggered workflow {} -> instance {} (attempt {}/{})",
                            scheduleKey, workflowId, instanceId, attempt + 1, retryCount + 1);
                    return;
                }
            } catch (Exception e) {
                if (attempt < retryCount) {
                    log.warn("Schedule {} failed to trigger workflow {} (attempt {}/{}): {}, retrying in {}s",
                            scheduleKey, workflowId, attempt + 1, retryCount + 1, e.getMessage(), retryIntervalSeconds);
                    sleepBeforeRetry(retryIntervalSeconds);
                } else {
                    log.error("Schedule {} failed to trigger workflow {} after {} attempts: {}",
                            scheduleKey, workflowId, retryCount + 1, e.getMessage(), e);
                }
            }
        }
    }

    private boolean isInstanceFailed(Long instanceId) {
        if (instanceId == null) {
            return true;
        }
        WorkflowInstance instance = workflowInstanceMapper.selectById(instanceId);
        // 实例不存在/状态未知时不再重试，避免重复执行
        return instance != null && "FAILED".equals(instance.getStatus());
    }

    private void sleepBeforeRetry(int intervalSeconds) {
        try {
            Thread.sleep(intervalSeconds * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Schedule retry sleep interrupted");
        }
    }
}
