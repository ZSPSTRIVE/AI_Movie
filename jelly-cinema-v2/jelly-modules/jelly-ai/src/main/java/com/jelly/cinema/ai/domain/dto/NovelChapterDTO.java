package com.jelly.cinema.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 小说章节生成请求 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class NovelChapterDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 书籍 ID
     */
    @NotBlank(message = "书籍ID不能为空")
    private String bookId;

    /**
     * 章节序号
     */
    @NotNull(message = "章节序号不能为空")
    private Integer chapterIndex;

    /**
     * 章节标题
     */
    @NotBlank(message = "章节标题不能为空")
    private String chapterTitle;

    /**
     * 大纲内容（JSON）
     */
    @NotBlank(message = "大纲内容不能为空")
    private String outline;

    /**
     * 字数要求
     */
    private Integer wordCount = 3000;
}
