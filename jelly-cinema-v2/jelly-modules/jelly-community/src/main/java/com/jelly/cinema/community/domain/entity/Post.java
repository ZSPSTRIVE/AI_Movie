package com.jelly.cinema.community.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_post")
public class Post extends BaseEntity {

    /**
     * 作者 ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 摘要
     */
    private String contentSummary;

    /**
     * 富文本内容
     */
    private String contentHtml;

    /**
     * 赞同数
     */
    private Integer voteUp;

    /**
     * 反对数
     */
    private Integer voteDown;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 评论数
     */
    private Integer commentCount;

    /**
     * 关联电影 ID
     */
    private Long filmId;

    /**
     * 状态：0-正常，1-删除
     */
    private Integer status;
}
