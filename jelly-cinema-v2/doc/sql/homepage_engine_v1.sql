-- ==========================================
-- 商业化首页内容运营引擎 - 数据库扩展 V1
-- 包含：配置版本管理、坑位规则、已发布内容快照
-- ==========================================

USE `jelly_cinema`;

-- -------------------------------------------
-- 1. 首页配置版本表
-- 用于管理草案、发布版本、回滚记录
-- -------------------------------------------
DROP TABLE IF EXISTS `t_homepage_config_version`;
CREATE TABLE `t_homepage_config_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version` VARCHAR(20) NOT NULL COMMENT '版本号 如 v1.0.0',
    `status` ENUM('draft','published','archived') DEFAULT 'draft' COMMENT '状态：草案/已发布/归档',
    `category` VARCHAR(20) NOT NULL COMMENT '分类：movie/tv_series/variety/anime',
    `config_json` MEDIUMTEXT COMMENT '完整配置JSON快照',
    `checksum` VARCHAR(64) DEFAULT NULL COMMENT '配置内容哈希（用于审计）',
    `publish_note` TEXT COMMENT '发布说明',
    `created_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人/操作人',
    `published_at` DATETIME DEFAULT NULL COMMENT '发布时间',
    `rollback_from` VARCHAR(20) DEFAULT NULL COMMENT '如果是回滚版本，记录来源版本号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_category` (`version`, `category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页配置版本表';

-- -------------------------------------------
-- 2. 坑位规则表
-- 定义每个板块的坑位数量、规则、锁定状态
-- -------------------------------------------
DROP TABLE IF EXISTS `t_slot_rule`;
CREATE TABLE `t_slot_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `category` VARCHAR(20) NOT NULL COMMENT '分类',
    `section_type` VARCHAR(30) NOT NULL COMMENT '板块：recommend/hot/new...',
    `slot_id` VARCHAR(50) NOT NULL COMMENT '坑位唯一标识',
    `position` INT NOT NULL COMMENT '排序位置',
    `locked` TINYINT DEFAULT 0 COMMENT '是否锁定不可替换（1=锁定）',
    `replaceable` TINYINT DEFAULT 1 COMMENT '是否可自动替换（1=是）',
    `min_rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '最低评分要求',
    `preferred_genres` VARCHAR(200) DEFAULT NULL COMMENT '偏好类型JSON数组',
    `preferred_regions` VARCHAR(100) DEFAULT NULL COMMENT '偏好地区',
    `year_range` VARCHAR(20) DEFAULT NULL COMMENT '年份范围如 2020-2025',
    `exposure_interval_days` INT DEFAULT 7 COMMENT '重复曝光间隔（天）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slot` (`category`, `section_type`, `slot_id`),
    KEY `idx_category_section` (`category`, `section_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页坑位规则表';

-- -------------------------------------------
-- 3. 已发布资源快照表
-- 前台直接读取此表，不再读取 t_homepage_content
-- -------------------------------------------
DROP TABLE IF EXISTS `t_published_content`;
CREATE TABLE `t_published_content` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_version` VARCHAR(20) NOT NULL COMMENT '所属配置版本',
    `category` VARCHAR(20) NOT NULL COMMENT '分类',
    `section_type` VARCHAR(30) NOT NULL COMMENT '板块',
    `slot_id` VARCHAR(50) NOT NULL COMMENT '对应坑位ID',
    `position` INT NOT NULL COMMENT '展示顺序',
    `canonical_id` VARCHAR(200) NOT NULL COMMENT '归一化资源ID',
    `tvbox_id` VARCHAR(200) DEFAULT NULL COMMENT '原始TVBox ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图',
    `play_url` VARCHAR(500) DEFAULT NULL COMMENT '播放地址',
    `rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '评分',
    `year` INT DEFAULT NULL COMMENT '年份',
    `region` VARCHAR(50) DEFAULT NULL COMMENT '地区',
    `source_name` VARCHAR(50) DEFAULT NULL COMMENT '来源',
    `locked` TINYINT DEFAULT 0 COMMENT '是否是锁定坑位',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_version` (`config_version`),
    INDEX `idx_category_section` (`category`, `section_type`),
    INDEX `idx_canonical_id` (`canonical_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='已发布首页内容快照';
