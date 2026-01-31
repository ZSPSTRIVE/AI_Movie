-- ==========================================
-- 首页内容表扩展 - AI推荐字段
-- ==========================================

USE `jelly_cinema`;

-- 添加 AI 精选标记字段
ALTER TABLE `t_homepage_content` 
ADD COLUMN `ai_best` TINYINT DEFAULT 0 COMMENT '是否AI精选: 0-否, 1-是' AFTER `ai_reason`;

-- 添加 话题热度值字段
ALTER TABLE `t_homepage_content` 
ADD COLUMN `trending_score` DECIMAL(10,2) DEFAULT 0.00 COMMENT '话题热度值' AFTER `ai_best`;

-- 为新字段添加索引以优化查询
CREATE INDEX `idx_ai_best` ON `t_homepage_content` (`ai_best`);
CREATE INDEX `idx_trending_score` ON `t_homepage_content` (`trending_score`);
