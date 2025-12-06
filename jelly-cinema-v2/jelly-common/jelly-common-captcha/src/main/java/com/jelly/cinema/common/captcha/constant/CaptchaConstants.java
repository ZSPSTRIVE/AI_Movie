package com.jelly.cinema.common.captcha.constant;

/**
 * 验证码常量
 *
 * @author Jelly Cinema
 */
public interface CaptchaConstants {

    /**
     * 缓存前缀
     */
    String CACHE_PREFIX = "jelly:captcha:";

    /**
     * 图片验证码 Key 前缀
     */
    String IMAGE_CAPTCHA_KEY = CACHE_PREFIX + "image:";

    /**
     * 邮箱验证码 Key 前缀
     */
    String EMAIL_CODE_KEY = CACHE_PREFIX + "email:";

    /**
     * 邮箱发送频率限制 Key 前缀
     */
    String EMAIL_RATE_LIMIT_KEY = CACHE_PREFIX + "rate:email:";

    /**
     * 邮箱每日发送次数 Key 前缀
     */
    String EMAIL_DAILY_COUNT_KEY = CACHE_PREFIX + "daily:email:";

    /**
     * 验证码错误次数 Key 前缀
     */
    String CODE_ERROR_COUNT_KEY = CACHE_PREFIX + "error:";

    /**
     * 图片验证码有效期（秒）- 2分钟
     */
    int IMAGE_CAPTCHA_EXPIRE = 120;

    /**
     * 邮箱验证码有效期（秒）- 5分钟
     */
    int EMAIL_CODE_EXPIRE = 300;

    /**
     * 验证码最大错误次数
     */
    int MAX_ERROR_COUNT = 5;

    /**
     * 错误次数锁定时间（秒）- 30分钟
     */
    int ERROR_LOCK_TIME = 1800;

    /**
     * 验证码业务类型
     */
    interface BusinessType {
        /**
         * 注册
         */
        String REGISTER = "register";

        /**
         * 登录
         */
        String LOGIN = "login";

        /**
         * 找回密码
         */
        String RESET_PASSWORD = "reset_password";

        /**
         * 绑定邮箱
         */
        String BIND_EMAIL = "bind_email";
    }
}
