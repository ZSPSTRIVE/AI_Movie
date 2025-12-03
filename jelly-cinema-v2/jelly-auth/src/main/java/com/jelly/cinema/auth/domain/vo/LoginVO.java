package com.jelly.cinema.auth.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录响应 VO
 *
 * @author Jelly Cinema
 */
@Data
@Builder
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
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
     * 角色
     */
    private String role;

    /**
     * Token
     */
    private String token;

    /**
     * Token 过期时间（秒）
     */
    private Long expireIn;
}
