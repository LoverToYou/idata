package com.idata.service.scheduler;

import com.idata.engine.workflow.DagExecutor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that triggers a workflow via cron. One job instance per schedule config,
 * the schedule id is part of the JobKey so each schedule runs independently.
 */
@DisallowConcurrentExecution
public class WorkflowTriggerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTriggerJob.class);

    @Autowired
    private DagExecutor dagExecutor;

    @Override
    public void execute(JobExecutionContext context) {
        Long workflowId = context.getMergedJobDataMap().getLong("workflowId");
        String scheduleId = context.getJobDetail().getKey().getName();
        try {
            Long instanceId = dagExecutor.execute(workflowId, "CRON");
            log.info("Schedule {} triggered workflow {} -> instance {}", scheduleId, workflowId, instanceId);
        } catch (Exception e) {
            log.error("Schedule {} failed to trigger workflow {}: {}", scheduleId, workflowId, e.getMessage(), e);
        }
    }
}
