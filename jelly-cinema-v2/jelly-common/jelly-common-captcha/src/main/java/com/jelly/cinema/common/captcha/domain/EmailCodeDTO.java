package com.jelly.cinema.common.captcha.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 *
 * @author Jelly Cinema
 */
@Data
public class EmailCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱地址
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 业务类型：register-注册, login-登录, reset_password-找回密码
     */
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    /**
     * 图片验证码（防止滥发）
     */
    private String captcha;

    /**
     * 图片验证码 Key
     */
    private String captchaKey;
}
