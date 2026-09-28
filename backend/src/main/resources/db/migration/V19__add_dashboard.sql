-- 看板系统：看板定义 + 卡片布局
CREATE TABLE dashboard (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(200) NOT NULL COMMENT '看板名称',
    description      VARCHAR(500) COMMENT '看板描述',
    refresh_interval INT NOT NULL DEFAULT 60 COMMENT '大屏自动刷新间隔(秒), 0=不自动刷新',
    created_at       DATETIME COMMENT '创建时间',
    updated_at       DATETIME COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '数据看板';

CREATE TABLE dashboard_item (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    dashboard_id BIGINT NOT NULL COMMENT '看板ID',
    report_id    BIGINT NOT NULL COMMENT '报表ID',
    title        VARCHAR(200) COMMENT '卡片标题(为空则用报表名)',
    pos_x        INT NOT NULL DEFAULT 0 COMMENT '栅格列位置(0-11)',
    pos_y        INT NOT NULL DEFAULT 0 COMMENT '栅格行位置',
    width        INT NOT NULL DEFAULT 4 COMMENT '卡片宽度(栅格列数)',
    height       INT NOT NULL DEFAULT 5 COMMENT '卡片高度(栅格行数)',
    KEY idx_dashboard_item_dashboard (dashboard_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '看板卡片';
