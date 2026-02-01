package com.jelly.cinema.ai.service;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import reactor.core.publisher.Flux;

/**
 * Agent 模式对话服务接口
 * 支持自动工具调用的智能对话
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface AgentChatService {

    /**
     * 同步对话（带工具调用）
     *
     * @param dto 对话请求
     * @return AI 回复
     */
    String chat(ChatRequestDTO dto);

    /**
     * 流式对话（带工具调用）
     *
     * @param dto 对话请求
     * @return 流式回复
     */
    Flux<String> chatStream(ChatRequestDTO dto);
}
