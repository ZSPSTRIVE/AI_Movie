package com.jelly.cinema.admin.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户列表 VO
 *
 * @author Jelly Cinema
 */
@Data
public class UserListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String phone;

    private String email;

    /**
     * 状态：0-正常, 1-封禁
     */
    private Integer status;

    /**
     * 封禁原因
     */
    private String banReason;

    /**
     * 封禁到期时间
     */
    private LocalDateTime banExpireTime;

    /**
     * 注册时间
     */
    private LocalDateTime createTime;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后登录IP
     */
    private String lastLoginIp;
}
