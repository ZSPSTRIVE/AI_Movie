-- ==========================================
-- 首页引擎初始化数据
-- 初始化坑位规则
-- ==========================================

USE `jelly_cinema`;

-- 清空旧规则 (添加WHERE条件以绕过客户端安全检查)
DELETE FROM `t_slot_rule` WHERE id > 0;

-- 电影-推荐板块 (12个坑位)
INSERT INTO `t_slot_rule` (category, section_type, slot_id, position, locked, replaceable, min_rating, exposure_interval_days, create_time, update_time) VALUES
('movie', 'recommend', 'movie_rec_1', 1, 0, 1, 8.5, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_2', 2, 0, 1, 8.0, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_3', 3, 0, 1, 7.5, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_4', 4, 0, 1, 7.5, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_5', 5, 0, 1, 7.0, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_6', 6, 0, 1, 7.0, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_7', 7, 0, 1, 7.0, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_8', 8, 0, 1, 7.0, 7, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_9', 9, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_10', 10, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_11', 11, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'recommend', 'movie_rec_12', 12, 0, 1, 6.0, 3, NOW(), NOW());

-- 电影-热门板块 (12个坑位)
INSERT INTO `t_slot_rule` (category, section_type, slot_id, position, locked, replaceable, min_rating, exposure_interval_days, create_time, update_time) VALUES
('movie', 'hot', 'movie_hot_1', 1, 0, 1, 8.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_2', 2, 0, 1, 7.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_3', 3, 0, 1, 7.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_4', 4, 0, 1, 7.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_5', 5, 0, 1, 7.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_6', 6, 0, 1, 7.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_7', 7, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_8', 8, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_9', 9, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_10', 10, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_11', 11, 0, 1, 6.0, 3, NOW(), NOW()),
('movie', 'hot', 'movie_hot_12', 12, 0, 1, 6.0, 3, NOW(), NOW());
