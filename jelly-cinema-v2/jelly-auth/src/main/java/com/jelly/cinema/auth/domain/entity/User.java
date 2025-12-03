package com.jelly.cinema.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BaseEntity {

    /**
     * 用户账号
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 角色：ROLE_USER, ROLE_ADMIN
     */
    private String role;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 状态：0-正常，1-禁用
     */
    private Integer status;
}
