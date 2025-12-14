-- ==========================================
-- 果冻影院 2.0 用户增长体系（jelly-auth）表结构
-- ==========================================

-- 积分资产表
CREATE TABLE IF NOT EXISTS `user_point` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `points` int NOT NULL DEFAULT '0' COMMENT '当前积分余额',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分表';

-- 积分流水表
CREATE TABLE IF NOT EXISTS `user_point_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `type` tinyint NOT NULL COMMENT '类型: 1-签到, 2-发帖, 3-兑换',
  `amount` int NOT NULL COMMENT '变动金额(+/-)',
  `remark` varchar(128) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- 优惠券模板表
CREATE TABLE IF NOT EXISTS `coupon_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(64) NOT NULL COMMENT '优惠券名称',
  `total_count` int NOT NULL COMMENT '总发行量',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已领取数量',
  `points_required` int NOT NULL DEFAULT '0' COMMENT '兑换所需积分',
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` tinyint DEFAULT '1' COMMENT '1-有效 0-下架',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

INSERT INTO `coupon_template` (`id`, `title`, `total_count`, `used_count`, `points_required`, `start_time`, `end_time`, `status`) VALUES
(10001, '观影券 · 全站通用 5 元', 2000, 0, 50,  DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(10002, '免广告体验卡 · 7 天', 500,  0, 120, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), 1),
(10003, '弹幕特权 · 彩色弹幕 30 天', 3000, 0, 30,  DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(10004, 'AI 加速卡 · 10 次', 1500, 0, 80,  DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), 1),
(10005, '会员体验卡 · 30 天', 100,  0, 888, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(10006, '直播礼物券 · 虚拟权益', 300,  0, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), 1)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

-- 用户优惠券表
CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `template_id` bigint NOT NULL,
  `status` tinyint DEFAULT '0' COMMENT '0-未使用 1-已使用 2-已过期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_template` (`user_id`,`template_id`) COMMENT '防止同一券重复领取'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- 签到归档表
CREATE TABLE IF NOT EXISTS `user_checkin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `checkin_date` date NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户签到归档表';
