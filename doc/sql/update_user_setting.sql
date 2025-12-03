-- ==========================================
-- 用户设置表增量脚本
-- 执行此脚本以添加用户设置表
-- ==========================================

USE `jelly_cinema`;

-- -------------------------------------------
-- 用户设置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_setting` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `enable_notification` TINYINT NOT NULL DEFAULT 1 COMMENT '开启消息通知：0-关闭, 1-开启',
    `enable_sound` TINYINT NOT NULL DEFAULT 1 COMMENT '消息提示音：0-关闭, 1-开启',
    `show_online_status` TINYINT NOT NULL DEFAULT 1 COMMENT '显示在线状态：0-隐藏, 1-显示',
    `allow_stranger_msg` TINYINT NOT NULL DEFAULT 0 COMMENT '允许陌生人消息：0-拒绝, 1-允许',
    `enter_to_send` TINYINT NOT NULL DEFAULT 1 COMMENT 'Enter发送消息：0-换行, 1-发送',
    `show_read_status` TINYINT NOT NULL DEFAULT 1 COMMENT '显示已读状态：0-隐藏, 1-显示',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';
