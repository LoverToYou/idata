package com.idata.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idata.entity.ScheduleConfig;
import com.idata.mapper.ScheduleConfigMapper;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges {@code schedule_config} records with the Quartz scheduler.
 * Uses the in-memory job store; all enabled schedules are re-registered on startup
 * so scheduling survives application restarts (state lives in the DB).
 */
@Service
public class WorkflowSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSchedulerService.class);
    private static final String GROUP = "workflow";

    private final Scheduler scheduler;
    private final ScheduleConfigMapper scheduleConfigMapper;

    public WorkflowSchedulerService(Scheduler scheduler, ScheduleConfigMapper scheduleConfigMapper) {
        this.scheduler = scheduler;
        this.scheduleConfigMapper = scheduleConfigMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAllEnabled() {
        List<ScheduleConfig> configs = scheduleConfigMapper.selectList(
                new LambdaQueryWrapper<ScheduleConfig>().eq(ScheduleConfig::getEnabled, true));
        log.info("Loading {} enabled schedule configs into Quartz", configs.size());
        for (ScheduleConfig config : configs) {
            register(config);
        }
    }

    /**
     * Register (or replace) a Quartz job+trigger for the given config.
     * Idempotent: an existing job with the same key is deleted first.
     */
    public void register(ScheduleConfig config) {
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        try {
            JobKey jobKey = jobKey(config.getId());
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            JobDetail job = JobBuilder.newJob(WorkflowTriggerJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("workflowId", config.getWorkflowId())
                    .usingJobData("scheduleId", config.getId())
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(config.getId()))
                    .withSchedule(CronScheduleBuilder.cronSchedule(config.getCronExpression())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .forJob(job)
                    .build();
            scheduler.scheduleJob(job, trigger);
            log.info("Registered schedule {} -> workflow {} cron {}", config.getId(), config.getWorkflowId(), config.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to register schedule {}: {}", config.getId(), e.getMessage(), e);
        }
    }

    public void reschedule(ScheduleConfig config) {
        register(config);
    }

    public void pause(Long id) {
        try {
            if (scheduler.checkExists(jobKey(id))) {
                scheduler.pauseJob(jobKey(id));
                log.info("Paused schedule {}", id);
            }
        } catch (Exception e) {
            log.error("Failed to pause schedule {}: {}", id, e.getMessage(), e);
        }
    }

    public void resume(Long id) {
        try {
            if (scheduler.checkExists(jobKey(id))) {
                scheduler.resumeJob(jobKey(id));
                log.info("Resumed schedule {}", id);
            } else {
                ScheduleConfig config = scheduleConfigMapper.selectById(id);
                if (config != null) {
                    register(config);
                }
            }
        } catch (Exception e) {
            log.error("Failed to resume schedule {}: {}", id, e.getMessage(), e);
        }
    }

    public void remove(Long id) {
        try {
            if (scheduler.checkExists(jobKey(id))) {
                scheduler.deleteJob(jobKey(id));
                log.info("Removed schedule {}", id);
            }
        } catch (Exception e) {
            log.error("Failed to remove schedule {}: {}", id, e.getMessage(), e);
        }
    }

    private JobKey jobKey(Long id) {
        return JobKey.jobKey("schedule-" + id, GROUP);
    }

    private TriggerKey triggerKey(Long id) {
        return TriggerKey.triggerKey("schedule-" + id, GROUP);
    }
}
