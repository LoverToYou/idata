-- 定时调度：失败自动重试配置
ALTER TABLE schedule_config
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '失败自动重试次数(0=不重试)',
    ADD COLUMN retry_interval_seconds INT NOT NULL DEFAULT 60 COMMENT '失败重试间隔(秒)';
