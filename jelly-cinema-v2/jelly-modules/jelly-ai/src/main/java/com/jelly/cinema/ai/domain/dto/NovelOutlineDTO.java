package com.jelly.cinema.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 小说大纲生成请求 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class NovelOutlineDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主题
     */
    @NotBlank(message = "主题不能为空")
    private String theme;

    /**
     * 风格
     */
    private String style = "轻松";

    /**
     * 主角名称
     */
    private String protagonist = "主角";

    /**
     * 章节数量
     */
    private Integer chapterCount = 10;

    /**
     * 额外要求
     */
    private String extraRequirements;
}
