package com.jelly.cinema.common.captcha.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 图片验证码响应
 *
 * @author Jelly Cinema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 验证码唯一标识
     */
    private String captchaKey;

    /**
     * 验证码图片（Base64 编码）
     */
    private String captchaImage;

    /**
     * 过期时间（秒）
     */
    private Integer expireSeconds;
}
