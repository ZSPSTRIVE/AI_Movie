package com.jelly.cinema.ai.service;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务接口
 *
 * @author Jelly Cinema
 */
public interface ChatService {

    /**
     * 同步对话
     *
     * @param dto 请求参数
     * @return AI 回复
     */
    String chat(ChatRequestDTO dto);

    /**
     * 流式对话（SSE）
     *
     * @param dto 请求参数
     * @return 流式响应
     */
    Flux<String> chatStream(ChatRequestDTO dto);
}
