-- ==========================================
-- 果冻影院 2.0 数据库初始化脚本
-- ==========================================

CREATE DATABASE IF NOT EXISTS `jelly_cinema` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `jelly_cinema`;

-- -------------------------------------------
-- 用户表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL COMMENT '用户ID（雪花算法）',
    `username` VARCHAR(50) NOT NULL COMMENT '用户账号',
    `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' COMMENT '角色：ROLE_USER, ROLE_ADMIN',
    `signature` VARCHAR(200) DEFAULT NULL COMMENT '个性签名',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常，1-删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 初始管理员账号 (密码: admin123)
INSERT INTO `t_user` (`id`, `username`, `password`, `nickname`, `avatar`, `role`) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', 'ROLE_ADMIN');

-- -------------------------------------------
-- 好友关系表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_friend`;
CREATE TABLE `t_friend` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `friend_id` BIGINT NOT NULL COMMENT '好友ID',
    `remark` VARCHAR(50) DEFAULT NULL COMMENT '备注名',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-拉黑',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
    KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友关系表';

-- -------------------------------------------
-- 电影分类表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_category`;
CREATE TABLE `t_category` (
    `id` BIGINT NOT NULL COMMENT '分类ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(200) DEFAULT NULL COMMENT '分类图标',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父级ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-启用，1-禁用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影分类表';

-- 初始分类数据
INSERT INTO `t_category` (`id`, `name`, `sort`) VALUES
(1, '电影', 1),
(2, '电视剧', 2),
(3, '综艺', 3),
(4, '动漫', 4),
(5, '纪录片', 5);

-- -------------------------------------------
-- 电影表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_film`;
CREATE TABLE `t_film` (
    `id` BIGINT NOT NULL COMMENT '电影ID',
    `title` VARCHAR(200) NOT NULL COMMENT '电影名',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `video_url` VARCHAR(500) DEFAULT NULL COMMENT '播放源地址',
    `description` TEXT COMMENT '简介',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `tags` JSON DEFAULT NULL COMMENT '标签（JSON数组）',
    `rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '评分（0-10）',
    `play_count` BIGINT NOT NULL DEFAULT 0 COMMENT '播放量',
    `year` INT DEFAULT NULL COMMENT '上映年份',
    `director` VARCHAR(100) DEFAULT NULL COMMENT '导演',
    `actors` VARCHAR(500) DEFAULT NULL COMMENT '主演',
    `region` VARCHAR(50) DEFAULT NULL COMMENT '地区',
    `duration` INT DEFAULT NULL COMMENT '时长（分钟）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-上架，1-下架',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_category` (`category_id`),
    KEY `idx_rating` (`rating`),
    KEY `idx_play_count` (`play_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影表';

-- 示例电影数据
INSERT INTO `t_film` (`id`, `title`, `cover_url`, `video_url`, `description`, `category_id`, `tags`, `rating`, `play_count`, `year`, `director`, `actors`, `region`, `duration`) VALUES
(1001, '流浪地球2', 'https://img.example.com/earth2.jpg', 'https://video.example.com/earth2.m3u8', '太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新的家园。然而宇宙之路危机四伏，为了拯救地球，流浪地球时代的年轻人再次挺身而出，展开争分夺秒的生死之战。', 1, '["科幻", "冒险", "灾难"]', 8.3, 1500000, 2023, '郭帆', '吴京,刘德华,李雪健', '中国大陆', 173),
(1002, '满江红', 'https://img.example.com/mjh.jpg', 'https://video.example.com/mjh.m3u8', '南宋绍兴年间，岳飞死后四年，秦桧率兵与金国会谈。会谈前夜，金国使者死在宰相驻地，一小兵被意外卷入一场巨大阴谋。', 1, '["悬疑", "喜剧", "历史"]', 7.8, 1200000, 2023, '张艺谋', '沈腾,易烊千玺,张译', '中国大陆', 159),
(1003, '三体', 'https://img.example.com/santi.jpg', 'https://video.example.com/santi.m3u8', '2007年，地球基础科学出现了异常的扰动，一时间科学界风雨飘摇，人心惶惶。离奇自杀的科学家，近乎神迹的倒计时，行为怪异的科学边界…纳米科学家汪淼被警官史强带到联合作战中心，并潜入名为"科学边界"的组织协助调查。', 2, '["科幻", "悬疑"]', 8.7, 2000000, 2023, '杨磊', '张鲁一,于和伟,陈瑾', '中国大陆', 45);

-- -------------------------------------------
-- 社区帖子表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_post`;
CREATE TABLE `t_post` (
    `id` BIGINT NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT NOT NULL COMMENT '作者ID',
    `title` VARCHAR(200) NOT NULL COMMENT '标题',
    `content_summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要',
    `content_html` LONGTEXT COMMENT '富文本内容',
    `vote_up` INT NOT NULL DEFAULT 0 COMMENT '赞同数',
    `vote_down` INT NOT NULL DEFAULT 0 COMMENT '反对数',
    `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览量',
    `comment_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
    `film_id` BIGINT DEFAULT NULL COMMENT '关联电影ID',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_film_id` (`film_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区帖子表';

-- -------------------------------------------
-- 评论表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment` (
    `id` BIGINT NOT NULL COMMENT '评论ID',
    `post_id` BIGINT NOT NULL COMMENT '帖子ID',
    `user_id` BIGINT NOT NULL COMMENT '评论者ID',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（回复）',
    `root_id` BIGINT DEFAULT NULL COMMENT '根评论ID',
    `reply_user_id` BIGINT DEFAULT NULL COMMENT '被回复者ID',
    `content` VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_root_id` (`root_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- -------------------------------------------
-- IM 消息表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_chat_msg`;
CREATE TABLE `t_chat_msg` (
    `id` BIGINT NOT NULL COMMENT '消息ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `from_id` BIGINT NOT NULL COMMENT '发送者ID',
    `to_id` BIGINT NOT NULL COMMENT '接收者ID（群聊为GroupId）',
    `cmd_type` TINYINT NOT NULL DEFAULT 1 COMMENT '消息类型：1-私聊，2-群聊',
    `msg_type` TINYINT NOT NULL DEFAULT 1 COMMENT '内容类型：1-文本，2-图片，3-文件，4-语音',
    `content` TEXT COMMENT '消息内容',
    `extra` JSON DEFAULT NULL COMMENT '扩展字段',
    `msg_seq` BIGINT NOT NULL COMMENT '消息序列号',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-撤回',
    `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态：0-未读，1-已读（仅私聊消息使用）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_from_id` (`from_id`),
    KEY `idx_to_id` (`to_id`),
    KEY `idx_msg_seq` (`msg_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM消息表';

-- -------------------------------------------
-- 群组表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_group`;
CREATE TABLE `t_group` (
    `id` BIGINT NOT NULL COMMENT '群ID（内部主键）',
    `group_no` VARCHAR(20) NOT NULL COMMENT '群号（6-8位，用于搜索展示）',
    `name` VARCHAR(100) NOT NULL COMMENT '群名称',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '群简介',
    `notice` TEXT COMMENT '群公告',
    `owner_id` BIGINT NOT NULL COMMENT '群主ID',
    `max_member` INT NOT NULL DEFAULT 200 COMMENT '群成员上限',
    `member_count` INT NOT NULL DEFAULT 1 COMMENT '当前成员数',
    `join_type` TINYINT NOT NULL DEFAULT 1 COMMENT '加群方式：0-自由加入, 1-需验证, 2-禁止加入',
    `is_mute_all` TINYINT NOT NULL DEFAULT 0 COMMENT '全员禁言：0-否, 1-是',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常，1-解散',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_no` (`group_no`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群组表';

-- -------------------------------------------
-- 群成员表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_group_member`;
CREATE TABLE `t_group_member` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `group_id` BIGINT NOT NULL COMMENT '群ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role` TINYINT NOT NULL DEFAULT 0 COMMENT '角色：0-普通成员, 1-管理员, 2-群主',
    `group_nick` VARCHAR(50) DEFAULT NULL COMMENT '群名片',
    `mute_end_time` DATETIME DEFAULT NULL COMMENT '禁言结束时间（NULL代表未禁言）',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入群时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员表';

-- -------------------------------------------
-- 申请记录表（好友申请、入群申请）
-- -------------------------------------------
DROP TABLE IF EXISTS `t_apply_record`;
CREATE TABLE `t_apply_record` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `type` TINYINT NOT NULL COMMENT '类型：1-好友申请, 2-入群申请',
    `from_id` BIGINT NOT NULL COMMENT '申请人ID',
    `target_id` BIGINT NOT NULL COMMENT '目标ID（好友申请为用户ID，入群申请为群ID）',
    `reason` VARCHAR(200) DEFAULT NULL COMMENT '验证消息/申请理由',
    `remark` VARCHAR(50) DEFAULT NULL COMMENT '备注名（好友申请时）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理, 1-已同意, 2-已拒绝, 3-已忽略',
    `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID（入群申请时为管理员ID）',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_from_id` (`from_id`),
    KEY `idx_target_id` (`target_id`),
    KEY `idx_type_status` (`type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请记录表';

-- -------------------------------------------
-- AI 知识库文档表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_knowledge_doc`;
CREATE TABLE `t_knowledge_doc` (
    `id` BIGINT NOT NULL COMMENT '文档ID',
    `doc_name` VARCHAR(200) NOT NULL COMMENT '文档名称',
    `doc_type` VARCHAR(20) DEFAULT NULL COMMENT '文档类型：pdf/word/txt',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-解析中，1-向量化完成，2-失败',
    `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件URL',
    `chunk_count` INT DEFAULT 0 COMMENT '分片数量',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI知识库文档表';

-- -------------------------------------------
-- 敏感词表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_sensitive_word`;
CREATE TABLE `t_sensitive_word` (
    `id` INT PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
    `word` VARCHAR(50) NOT NULL COMMENT '敏感词',
    `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1-政治, 2-色情, 3-暴恐, 4-广告, 5-其他',
    `strategy` TINYINT NOT NULL DEFAULT 1 COMMENT '策略：1-替换为***, 2-直接拦截',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-禁用, 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词表';

-- 初始敏感词数据
INSERT INTO `t_sensitive_word` (`word`, `type`, `strategy`) VALUES
('测试敏感词', 5, 1);

-- -------------------------------------------
-- 用户收藏表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_user_favorite`;
CREATE TABLE `t_user_favorite` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `film_id` BIGINT NOT NULL COMMENT '电影ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_film` (`user_id`, `film_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_film_id` (`film_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- -------------------------------------------
-- 观看历史表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_watch_history`;
CREATE TABLE `t_watch_history` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `film_id` BIGINT NOT NULL COMMENT '电影ID',
    `progress` INT NOT NULL DEFAULT 0 COMMENT '观看进度（百分比0-100）',
    `watch_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后观看时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_film` (`user_id`, `film_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_watch_time` (`watch_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='观看历史表';

-- -------------------------------------------
-- 举报记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_report`;
CREATE TABLE `t_report` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `reporter_id` BIGINT NOT NULL COMMENT '举报人ID',
    `target_id` BIGINT NOT NULL COMMENT '被举报ID（人/群/消息）',
    `target_type` TINYINT NOT NULL COMMENT '类型：1-用户, 2-群组, 3-消息, 4-帖子',
    `reason` VARCHAR(50) DEFAULT NULL COMMENT '举报原因',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '详细描述',
    `evidence_imgs` VARCHAR(2000) DEFAULT NULL COMMENT '截图证据URL（JSON数组）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理, 1-已处理, 2-已忽略',
    `result` VARCHAR(200) DEFAULT NULL COMMENT '处理结果',
    `handler_id` BIGINT DEFAULT NULL COMMENT '处理人ID',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_reporter_id` (`reporter_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='举报记录表';

-- -------------------------------------------
-- 管理员操作日志表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_admin_log`;
CREATE TABLE `t_admin_log` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `admin_id` BIGINT NOT NULL COMMENT '管理员ID',
    `admin_name` VARCHAR(50) DEFAULT NULL COMMENT '管理员名称',
    `module` VARCHAR(50) NOT NULL COMMENT '模块：用户/群组/内容',
    `action` VARCHAR(50) NOT NULL COMMENT '动作：封禁/解封/解散/删除',
    `target_id` BIGINT DEFAULT NULL COMMENT '操作对象ID',
    `target_type` VARCHAR(20) DEFAULT NULL COMMENT '对象类型：user/group/post',
    `detail` VARCHAR(500) DEFAULT NULL COMMENT '操作详情/理由',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_module` (`module`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- -------------------------------------------
-- 用户封禁记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_user_ban`;
CREATE TABLE `t_user_ban` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `ban_type` TINYINT NOT NULL DEFAULT 1 COMMENT '封禁类型：1-登录封禁, 2-发言禁止, 3-全部',
    `duration` INT NOT NULL DEFAULT 0 COMMENT '封禁时长（小时），0表示永久',
    `reason` VARCHAR(200) DEFAULT NULL COMMENT '封禁原因',
    `expire_time` DATETIME DEFAULT NULL COMMENT '解封时间（NULL表示永久）',
    `operator_id` BIGINT NOT NULL COMMENT '操作人ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-已解除, 1-生效中',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户封禁记录表';

-- -------------------------------------------
-- 用户登录日志表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_login_log`;
CREATE TABLE `t_login_log` (
    `id` BIGINT NOT NULL COMMENT 'ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `login_ip` VARCHAR(50) DEFAULT NULL COMMENT '登录IP',
    `login_location` VARCHAR(100) DEFAULT NULL COMMENT '登录地点',
    `browser` VARCHAR(50) DEFAULT NULL COMMENT '浏览器类型',
    `os` VARCHAR(50) DEFAULT NULL COMMENT '操作系统',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-失败, 1-成功',
    `msg` VARCHAR(200) DEFAULT NULL COMMENT '提示消息',
    `login_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录日志表';

-- -------------------------------------------
-- 用户设置表
-- -------------------------------------------
DROP TABLE IF EXISTS `t_user_setting`;
CREATE TABLE `t_user_setting` (
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
