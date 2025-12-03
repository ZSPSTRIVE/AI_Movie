package com.jelly.cinema.community.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子 VO
 *
 * @author Jelly Cinema
 */
@Data
public class PostVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String userAvatar;

    private String title;

    private String contentSummary;

    private String contentHtml;

    private Integer voteUp;

    private Integer voteDown;

    private Integer viewCount;

    private Integer commentCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long filmId;

    private String filmTitle;

    private LocalDateTime createTime;

    /**
     * 当前用户投票状态：1-赞同，-1-反对，0-未投票
     */
    private Integer voteStatus;
}
