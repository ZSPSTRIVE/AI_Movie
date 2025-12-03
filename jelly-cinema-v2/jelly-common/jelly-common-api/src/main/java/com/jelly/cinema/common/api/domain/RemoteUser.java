package com.jelly.cinema.common.api.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 远程用户信息
 *
 * @author Jelly Cinema
 */
@Data
public class RemoteUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
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
     * 个性签名
     */
    private String signature;

    /**
     * 角色
     */
    private String role;
}
