package com.jelly.cinema.community.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 VO
 *
 * @author Jelly Cinema
 */
@Data
public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long postId;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private Long parentId;

    private Long rootId;

    private Long replyUserId;

    private String replyNickname;

    private String content;

    private Integer likeCount;

    private LocalDateTime createTime;

    /**
     * 当前用户是否已点赞
     */
    private Boolean liked;

    /**
     * 子评论列表
     */
    private List<CommentVO> children;
}
