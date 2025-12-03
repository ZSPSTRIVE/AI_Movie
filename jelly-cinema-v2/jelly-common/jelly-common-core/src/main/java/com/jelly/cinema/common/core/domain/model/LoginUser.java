package com.jelly.cinema.common.core.domain.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录用户信息
 *
 * @author Jelly Cinema
 */
@Data
@NoArgsConstructor
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 登录 IP
     */
    private String loginIp;

    /**
     * Token
     */
    private String token;

    /**
     * Token 过期时间
     */
    private Long expireTime;

    public LoginUser(Long userId, String username, String nickname, String avatar, String role) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.avatar = avatar;
        this.role = role;
        this.loginTime = LocalDateTime.now();
    }
}
