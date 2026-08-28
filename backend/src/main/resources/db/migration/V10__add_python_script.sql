CREATE TABLE python_script (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100) NOT NULL COMMENT '脚本名称',
    description      VARCHAR(500) COMMENT '脚本描述',
    content          LONGTEXT NOT NULL COMMENT '脚本源码',
    timeout_seconds  INT DEFAULT 60 COMMENT '超时秒数',
    cron_expression  VARCHAR(100) COMMENT 'cron表达式(空=不调度)',
    cron_enabled     TINYINT(1) DEFAULT 0 COMMENT '是否启用定时调度',
    created_at       DATETIME COMMENT '创建时间',
    updated_at       DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'Python 脚本表';

CREATE TABLE python_run (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id    BIGINT NOT NULL COMMENT '脚本ID',
    params       TEXT COMMENT '执行参数(传入stdin)',
    status       VARCHAR(20) DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FAILED/CANCELLED',
    stdout       LONGTEXT COMMENT '标准输出',
    stderr       LONGTEXT COMMENT '标准错误',
    exit_code    INT COMMENT '退出码',
    triggered_by VARCHAR(20) DEFAULT 'MANUAL' COMMENT 'MANUAL/CRON',
    started_at   DATETIME COMMENT '开始时间',
    finished_at  DATETIME COMMENT '结束时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT 'Python 脚本执行记录表';
