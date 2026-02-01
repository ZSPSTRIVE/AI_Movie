-- ==========================================
-- 💡 智能对话日志表 (Enterprise Audit Log)
-- 用于记录所有 AI 对话、Token 消耗、响应时间及用户反馈
-- ==========================================

DROP TABLE IF EXISTS `t_ai_chat_log`;
CREATE TABLE `t_ai_chat_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID',
  
  -- 输入信息
  `query` text NOT NULL COMMENT '用户提问',
  `intent` varchar(32) DEFAULT NULL COMMENT '识别意图',
  `tools_used` varchar(255) DEFAULT NULL COMMENT '调用工具列表',
  
  -- 输出信息
  `response` text COMMENT 'AI响应内容',
  `rag_sources` json DEFAULT NULL COMMENT 'RAG参考源',
  
  -- 性能指标
  `prompt_tokens` int(11) DEFAULT 0 COMMENT '提示词Token',
  `completion_tokens` int(11) DEFAULT 0 COMMENT '生成Token',
  `total_tokens` int(11) DEFAULT 0 COMMENT '总Token',
  `latency_ms` int(11) DEFAULT 0 COMMENT '总耗时(ms)',
  `rag_latency_ms` int(11) DEFAULT 0 COMMENT 'RAG检索耗时(ms)',
  
  -- 反馈
  `feedback_score` tinyint(2) DEFAULT NULL COMMENT '用户评分(1-5)',
  `feedback_text` varchar(255) DEFAULT NULL COMMENT '反馈备注',
  
  -- 状态
  `status` tinyint(2) DEFAULT 1 COMMENT '状态: 0-失败 1-成功 2-中断',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话审计日志表';
