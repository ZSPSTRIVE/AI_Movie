-- 添加消息已读状态字段
-- 用于私聊消息的已读/未读状态追踪
ALTER TABLE `t_chat_msg` 
ADD COLUMN `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态：0-未读，1-已读（仅私聊消息使用）' AFTER `status`;

-- 为已有的消息设置默认已读状态（可选，历史消息可以默认标记为已读）
-- UPDATE `t_chat_msg` SET `read_status` = 1 WHERE `cmd_type` = 1;
