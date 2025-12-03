package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 申请记录 VO
 *
 * @author Jelly Cinema
 */
@Data
public class ApplyRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 申请ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 类型：1-好友申请, 2-入群申请
     */
    private Integer type;

    /**
     * 申请人ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromId;

    /**
     * 申请人昵称
     */
    private String fromNickname;

    /**
     * 申请人头像
     */
    private String fromAvatar;

    /**
     * 目标ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /**
     * 目标名称（好友申请为空，入群申请为群名）
     */
    private String targetName;

    /**
     * 验证消息
     */
    private String reason;

    /**
     * 状态：0-待处理, 1-已同意, 2-已拒绝, 3-已忽略
     */
    private Integer status;

    /**
     * 申请时间
     */
    private LocalDateTime createTime;
}
