-- 看板：全局筛选配置 + 大屏等比缩放适配
ALTER TABLE dashboard
    ADD COLUMN filters   TEXT COMMENT '大屏全局筛选配置JSON: [{key,label,type,defaultValue,options}]',
    ADD COLUMN fit_scale TINYINT(1) NOT NULL DEFAULT 1 COMMENT '大屏等比缩放适配(按1920x1080设计稿)';
