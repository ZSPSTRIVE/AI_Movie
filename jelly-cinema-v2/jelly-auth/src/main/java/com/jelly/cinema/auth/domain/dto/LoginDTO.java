package com.jelly.cinema.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名（也可以是邮箱）
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 图片验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String captcha;

    /**
     * 图片验证码 Key
     */
    @NotBlank(message = "验证码Key不能为空")
    private String captchaKey;

    /**
     * 邮箱验证码（异常登录或首次登录时需要）
     */
    private String emailCode;

    /**
     * 邮箱（用于发送验证码）
     */
    private String email;
}
