package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话 VO
 *
 * @author Jelly Cinema
 */
@Data
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 会话类型：1-单聊，2-群聊
     */
    private Integer type;

    /**
     * 对方用户 ID（单聊时）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 群 ID（群聊时）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /**
     * 对方昵称 / 群名称
     */
    private String nickname;

    /**
     * 对方头像
     */
    private String avatar;

    /**
     * 最后一条消息内容
     */
    private String lastMessage;

    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastTime;

    /**
     * 未读消息数
     */
    private Integer unreadCount;
}
