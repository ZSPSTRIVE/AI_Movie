package com.jelly.cinema.community.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_comment")
public class Comment extends BaseEntity {

    /**
     * 帖子 ID
     */
    private Long postId;

    /**
     * 评论者 ID
     */
    private Long userId;

    /**
     * 父评论 ID（回复）
     */
    private Long parentId;

    /**
     * 根评论 ID
     */
    private Long rootId;

    /**
     * 被回复者 ID
     */
    private Long replyUserId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 状态：0-正常，1-删除
     */
    private Integer status;
}
