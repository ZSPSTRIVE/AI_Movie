package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 好友信息 VO
 *
 * @author Jelly Cinema
 */
@Data
public class FriendVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 好友用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 备注名
     */
    private String remark;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 状态：0-正常，1-已拉黑
     */
    private Integer status;

    /**
     * 是否在线
     */
    private Boolean online;

    /**
     * 添加时间
     */
    private LocalDateTime createTime;
}
