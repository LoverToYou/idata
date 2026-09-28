-- 文件夹：用于报表 / 数据看板的分组管理
CREATE TABLE folder (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(200) NOT NULL COMMENT '文件夹名称',
    parent_id  BIGINT COMMENT '父文件夹ID(NULL=根目录)',
    biz_type   VARCHAR(20) NOT NULL COMMENT '业务类型: REPORT / DASHBOARD',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_at DATETIME COMMENT '创建时间',
    updated_at DATETIME COMMENT '更新时间',
    KEY idx_folder_parent (biz_type, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '文件夹';

ALTER TABLE report ADD COLUMN folder_id BIGINT COMMENT '所属文件夹ID(NULL=未分组)';
ALTER TABLE dashboard ADD COLUMN folder_id BIGINT COMMENT '所属文件夹ID(NULL=未分组)';
