package com.jelly.cinema.ai.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 对话请求 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class ChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户问题
     */
    private String prompt;

    /**
     * 历史对话
     */
    private List<Message> history;

    /**
     * 模型名称
     */
    private String model = "deepseek-chat";

    /**
     * 是否启用 RAG
     */
    private Boolean enableRag = false;

    /**
     * 关联电影 ID（用于上下文）
     */
    private Long filmId;

    @Data
    public static class Message {
        private String role;  // user / assistant
        private String content;
    }
}
