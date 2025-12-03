package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息 VO
 *
 * @author Jelly Cinema
 */
@Data
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String sessionId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromId;

    private String fromNickname;

    private String fromAvatar;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long toId;

    private Integer cmdType;

    private Integer msgType;

    private String content;

    private String extra;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long msgSeq;

    private LocalDateTime createTime;

    /**
     * 消息状态：0-正常，1-撤回
     */
    private Integer status;

    /**
     * 已读状态：0-未读，1-已读（仅私聊消息使用）
     */
    private Integer readStatus;
}
