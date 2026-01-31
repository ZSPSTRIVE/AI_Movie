-- ==========================================
-- 智能内容管理系统 - 数据库扩展表
-- 适用于果冻影院 2.0
-- ==========================================

USE `jelly_cinema`;

-- -------------------------------------------
-- 首页内容表（管理员控制的首页展示内容）
-- -------------------------------------------
DROP TABLE IF EXISTS `t_homepage_content`;
CREATE TABLE `t_homepage_content` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content_type` VARCHAR(20) NOT NULL DEFAULT 'movie' COMMENT '内容类型：movie/tv_series/variety/anime',
    `section_type` VARCHAR(30) NOT NULL DEFAULT 'recommend' COMMENT '板块类型：recommend/hot/new/trending',
    `tvbox_id` VARCHAR(200) NOT NULL COMMENT 'TVBox电影ID（Base64编码）',
    `title` VARCHAR(200) NOT NULL COMMENT '电影/剧集标题',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `description` TEXT COMMENT '简介',
    `source_name` VARCHAR(50) DEFAULT NULL COMMENT '来源名称（量子/非凡等）',
    `source_api` VARCHAR(500) DEFAULT NULL COMMENT '来源API地址',
    `rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '评分',
    `year` INT DEFAULT NULL COMMENT '年份',
    `region` VARCHAR(50) DEFAULT NULL COMMENT '地区',
    `actors` VARCHAR(500) DEFAULT NULL COMMENT '主演',
    `director` VARCHAR(100) DEFAULT NULL COMMENT '导演',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序（越小越靠前）',
    `ai_score` DECIMAL(5,2) DEFAULT NULL COMMENT 'AI推荐分数',
    `ai_reason` VARCHAR(500) DEFAULT NULL COMMENT 'AI推荐理由',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tvbox_section` (`tvbox_id`, `section_type`),
    KEY `idx_content_type` (`content_type`),
    KEY `idx_section_type` (`section_type`),
    KEY `idx_sort_order` (`sort_order`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页内容表';

-- -------------------------------------------
-- TVBox采集源配置表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_tvbox_source`;
CREATE TABLE `t_tvbox_source` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `source_name` VARCHAR(50) NOT NULL COMMENT '源名称',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'API地址',
    `api_type` VARCHAR(20) NOT NULL DEFAULT 'json' COMMENT 'API类型：json/xml',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    `priority` INT NOT NULL DEFAULT 100 COMMENT '优先级（越小越优先）',
    `fetch_interval` INT NOT NULL DEFAULT 60 COMMENT '采集间隔（分钟）',
    `last_fetch_time` DATETIME DEFAULT NULL COMMENT '上次采集时间',
    `fetch_status` TINYINT NOT NULL DEFAULT 0 COMMENT '采集状态：0-正常，1-失败，2-采集中',
    `film_count` INT NOT NULL DEFAULT 0 COMMENT '当前采集电影数量',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_name` (`source_name`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TVBox采集源配置表';

-- 初始化采集源配置
INSERT INTO `t_tvbox_source` (`source_name`, `api_url`, `priority`, `enabled`) VALUES
('量子资源', 'https://cj.lziapi.com/api.php/provide/vod/', 1, 1),
('非凡资源', 'https://cj.ffzyapi.com/api.php/provide/vod/', 2, 1),
('红牛资源', 'https://www.hongniuzy2.com/api.php/provide/vod/', 3, 1),
('卧龙资源', 'https://collect.wolongzy.cc/api.php/provide/vod/', 4, 1),
('新浪资源', 'https://api.xinlangapi.com/xinlangapi.php/provide/vod/', 5, 1),
('光速资源', 'https://api.guangsuapi.com/api.php/provide/vod/', 6, 1);

-- -------------------------------------------
-- 系统配置表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_system_config`;
CREATE TABLE `t_system_config` (
    `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_group` VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '配置组',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `config_type` VARCHAR(20) NOT NULL DEFAULT 'string' COMMENT '值类型：string/number/json/boolean',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '配置说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 初始化系统配置
INSERT INTO `t_system_config` (`config_group`, `config_key`, `config_value`, `config_type`, `description`) VALUES
('tvbox', 'auto_fetch_enabled', 'true', 'boolean', '是否启用自动采集'),
('tvbox', 'fetch_interval_minutes', '60', 'number', '自动采集间隔（分钟）'),
('tvbox', 'max_homepage_movies', '18', 'number', '首页最大电影数量'),
('tvbox', 'max_homepage_tv', '12', 'number', '首页最大电视剧数量'),
('tvbox', 'cache_ttl_seconds', '300', 'number', 'Redis缓存过期时间（秒）'),
('ai', 'enabled', 'true', 'boolean', '是否启用AI排序'),
('ai', 'api_provider', 'guiji', 'string', 'AI服务商：guiji/openai/deepseek'),
('ai', 'sort_frequency', 'daily', 'string', 'AI排序频率：hourly/daily/weekly'),
('ai', 'last_sort_time', NULL, 'string', '上次AI排序时间');

-- -------------------------------------------
-- 内容刷新日志表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_content_refresh_log`;
CREATE TABLE `t_content_refresh_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `refresh_type` VARCHAR(20) NOT NULL COMMENT '刷新类型：manual/auto/ai_sort',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID（自动刷新为空）',
    `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人名称',
    `source_count` INT DEFAULT 0 COMMENT '采集源数量',
    `film_count` INT DEFAULT 0 COMMENT '采集电影数量',
    `success_count` INT DEFAULT 0 COMMENT '成功数量',
    `fail_count` INT DEFAULT 0 COMMENT '失败数量',
    `duration_ms` BIGINT DEFAULT 0 COMMENT '耗时（毫秒）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败，1-成功，2-进行中',
    `error_msg` TEXT COMMENT '错误信息',
    `detail` JSON COMMENT '详细日志（JSON）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_refresh_type` (`refresh_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容刷新日志表';
