-- ==========================================
-- Homepage AI fields (idempotent)
-- Safe to run multiple times
-- ==========================================

USE `jelly_cinema`;

SET @db := DATABASE();

-- ai_best
SET @has_ai_best := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 't_homepage_content'
      AND COLUMN_NAME = 'ai_best'
);
SET @sql := IF(
    @has_ai_best = 0,
    'ALTER TABLE `t_homepage_content` ADD COLUMN `ai_best` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否AI精选: 0-否, 1-是'' AFTER `ai_reason`',
    'SELECT ''skip: ai_best already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- trending_score
SET @has_trending_score := (
    SELECT COUNT(1)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 't_homepage_content'
      AND COLUMN_NAME = 'trending_score'
);
SET @sql := IF(
    @has_trending_score = 0,
    'ALTER TABLE `t_homepage_content` ADD COLUMN `trending_score` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''话题热度值'' AFTER `ai_best`',
    'SELECT ''skip: trending_score already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- idx_ai_best
SET @has_idx_ai_best := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 't_homepage_content'
      AND INDEX_NAME = 'idx_ai_best'
);
SET @sql := IF(
    @has_idx_ai_best = 0,
    'CREATE INDEX `idx_ai_best` ON `t_homepage_content` (`ai_best`)',
    'SELECT ''skip: idx_ai_best already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- idx_trending_score
SET @has_idx_trending_score := (
    SELECT COUNT(1)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 't_homepage_content'
      AND INDEX_NAME = 'idx_trending_score'
);
SET @sql := IF(
    @has_idx_trending_score = 0,
    'CREATE INDEX `idx_trending_score` ON `t_homepage_content` (`trending_score`)',
    'SELECT ''skip: idx_trending_score already exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
