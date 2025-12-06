package com.jelly.cinema.common.captcha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件配置属性
 *
 * @author Jelly Cinema
 */
@Data
@ConfigurationProperties(prefix = "mail")
public class MailProperties {

    /**
     * 发件人邮箱
     */
    private String from;

    /**
     * 邮箱授权码
     */
    private String password;

    /**
     * SMTP 服务器地址
     */
    private String host;

    /**
     * SMTP 服务器端口
     */
    private Integer port = 465;

    /**
     * 管理员邮箱
     */
    private String admin;

    /**
     * 是否启用 SSL
     */
    private Boolean ssl = true;

    /**
     * 验证码有效期（秒）
     */
    private Integer codeExpireSeconds = 300;

    /**
     * 同一邮箱发送间隔（秒）
     */
    private Integer sendIntervalSeconds = 60;

    /**
     * 每日发送限制次数
     */
    private Integer dailyLimit = 10;
}
