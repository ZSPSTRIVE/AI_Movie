package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员 VO
 *
 * @author Jelly Cinema
 */
@Data
public class GroupMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

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
     * 群名片
     */
    private String groupNick;

    /**
     * 角色：0-普通成员, 1-管理员, 2-群主
     */
    private Integer role;

    /**
     * 禁言结束时间
     */
    private LocalDateTime muteEndTime;

    /**
     * 入群时间
     */
    private LocalDateTime joinTime;

    /**
     * 是否被禁言中
     */
    private Boolean isMuted;

    /**
     * 获取显示名称（优先群名片，其次昵称，最后用户名）
     */
    public String getDisplayName() {
        if (groupNick != null && !groupNick.isEmpty()) {
            return groupNick;
        }
        if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        }
        return username;
    }
}
