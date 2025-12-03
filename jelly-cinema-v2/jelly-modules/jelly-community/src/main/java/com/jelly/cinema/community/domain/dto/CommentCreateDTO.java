package com.jelly.cinema.community.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布评论 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class CommentCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 帖子 ID
     */
    @NotNull(message = "帖子ID不能为空")
    private Long postId;

    /**
     * 评论内容
     */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最多1000字")
    private String content;

    /**
     * 父评论 ID（回复时使用）
     */
    private Long parentId;

    /**
     * 被回复者 ID
     */
    private Long replyUserId;
}
