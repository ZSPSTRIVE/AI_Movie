package com.jelly.cinema.admin.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户详情 VO
 *
 * @author Jelly Cinema
 */
@Data
public class UserDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String signature;

    private String phone;

    private String email;

    private Integer status;

    private String banReason;

    private LocalDateTime banExpireTime;

    private LocalDateTime createTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    /**
     * 好友列表
     */
    private List<FriendItem> friends;

    /**
     * 加入的群组
     */
    private List<GroupItem> groups;

    /**
     * 登录日志
     */
    private List<LoginLogItem> loginLogs;

    @Data
    public static class FriendItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String nickname;
        private String avatar;
    }

    @Data
    public static class GroupItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String name;
        private String avatar;
        private Integer memberCount;
    }

    @Data
    public static class LoginLogItem {
        private String ip;
        private String location;
        private LocalDateTime loginTime;
    }
}
