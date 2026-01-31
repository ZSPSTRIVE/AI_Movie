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
     * 关联电影 ID（用于上下文，TVBox为Base64编码字符串）
     */
    private String filmId;

    /**
     * 电影上下文信息（用于AI理解当前电影详情）
     */
    private FilmContext filmContext;

    /**
     * 电影上下文信息
     */
    @Data
    public static class FilmContext implements Serializable {
        private static final long serialVersionUID = 1L;

        private String tvboxId;      // TVBox ID
        private String title;        // 标题
        private String description;  // 简介
        private String actors;       // 演员
        private String director;     // 导演
        private String year;         // 年份
        private String region;       // 地区
        private String category;     // 类型
    }

    @Data
    public static class Message {
        private String role;  // user / assistant
        private String content;
    }
}
