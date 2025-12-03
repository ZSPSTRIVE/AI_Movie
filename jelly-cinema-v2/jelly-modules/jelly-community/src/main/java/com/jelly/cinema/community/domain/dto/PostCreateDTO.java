package com.jelly.cinema.community.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布帖子 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class PostCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多200字")
    private String title;

    /**
     * 富文本内容
     */
    @NotBlank(message = "内容不能为空")
    private String contentHtml;

    /**
     * 关联电影 ID
     */
    private Long filmId;
}
