-- ============================================================
-- Eagle 配置中心 数据库初始化脚本 (MySQL 8.x)
-- 说明: 表结构与《配置中心.md》文档第二节一致;
--       额外补充的普通索引仅用于加速查询, 不改变表结构语义
-- ============================================================

CREATE DATABASE IF NOT EXISTS `eagle` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `eagle`;

-- ------------------------------------------------------------
-- 1. 配置主表 t_config_info
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_config_info` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    env VARCHAR(32) NOT NULL COMMENT '环境: dev/test/prod',
    group_id VARCHAR(64) NOT NULL COMMENT '服务分组',
    config_key VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置原始值',
    value_type VARCHAR(20) NOT NULL DEFAULT 'string' COMMENT '类型: string / number / boolean',
    description VARCHAR(512) DEFAULT '' COMMENT '配置说明',
    is_deleted TINYINT DEFAULT 0 COMMENT '软删除标记',
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_env_group_key (env, group_id, config_key),
    KEY idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 2. 版本历史表 t_config_history
--    同一配置项独立递增版本号(item_version), 每次发布保存快照
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_config_history` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键',
    config_id BIGINT NOT NULL COMMENT '关联t_config_info主键',
    env VARCHAR(32) NOT NULL,
    group_id VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT COMMENT '快照值',
    value_type VARCHAR(20) NOT NULL DEFAULT 'string',
    item_version INT NOT NULL COMMENT '当前配置项版本号，同config_id下自增',
    operation VARCHAR(16) NOT NULL COMMENT '操作类型 UPDATE / DELETE',
    operator VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_config_id (config_id, item_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- 3. 发布记录表 t_config_publish
--    记录每一次发布状态, 用于追踪发布结果、失败重试
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_config_publish` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '发布记录主键',
    env VARCHAR(32) NOT NULL,
    group_id VARCHAR(64) NOT NULL,
    config_key VARCHAR(128) NOT NULL,
    config_history_id BIGINT NOT NULL COMMENT '关联t_config_history版本快照ID',
    publish_status TINYINT DEFAULT 0 COMMENT '0待发布 1成功 2失败',
    publish_msg VARCHAR(512) COMMENT '失败信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_env_group_key (env, group_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
