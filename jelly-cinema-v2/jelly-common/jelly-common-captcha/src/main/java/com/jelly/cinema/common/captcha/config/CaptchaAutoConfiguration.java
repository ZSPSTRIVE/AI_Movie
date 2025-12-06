package com.jelly.cinema.common.captcha.config;

import com.google.code.kaptcha.Producer;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 验证码模块自动配置
 *
 * @author Jelly Cinema
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
@ComponentScan("com.jelly.cinema.common.captcha")
public class CaptchaAutoConfiguration {

    /**
     * 配置图片验证码生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public Producer kaptchaProducer() {
        DefaultKaptcha kaptcha = new DefaultKaptcha();
        Properties properties = new Properties();
        
        // 图片边框
        properties.setProperty("kaptcha.border", "yes");
        properties.setProperty("kaptcha.border.color", "105,179,90");
        
        // 字体颜色
        properties.setProperty("kaptcha.textproducer.font.color", "blue");
        
        // 图片宽高
        properties.setProperty("kaptcha.image.width", "120");
        properties.setProperty("kaptcha.image.height", "40");
        
        // 字体大小
        properties.setProperty("kaptcha.textproducer.font.size", "32");
        
        // Session key
        properties.setProperty("kaptcha.session.key", "captcha_code");
        
        // 验证码长度
        properties.setProperty("kaptcha.textproducer.char.length", "4");
        
        // 字体
        properties.setProperty("kaptcha.textproducer.font.names", "宋体,楷体,微软雅黑");
        
        // 图片样式: 水纹 com.google.code.kaptcha.impl.WaterRipple
        // 鱼眼 com.google.code.kaptcha.impl.FishEyeGimpy
        // 阴影 com.google.code.kaptcha.impl.ShadowGimpy
        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.ShadowGimpy");
        
        // 干扰实现类
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.DefaultNoise");
        
        // 干扰颜色
        properties.setProperty("kaptcha.noise.color", "black");
        
        // 背景颜色渐变，开始颜色
        properties.setProperty("kaptcha.background.clear.from", "lightGray");
        
        // 背景颜色渐变，结束颜色
        properties.setProperty("kaptcha.background.clear.to", "white");
        
        // 文字间隔
        properties.setProperty("kaptcha.textproducer.char.space", "4");
        
        Config config = new Config(properties);
        kaptcha.setConfig(config);
        return kaptcha;
    }

    /**
     * 配置邮件发送器
     */
    @Bean
    @ConditionalOnMissingBean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailProperties.getHost());
        mailSender.setPort(mailProperties.getPort());
        mailSender.setUsername(mailProperties.getFrom());
        mailSender.setPassword(mailProperties.getPassword());
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        
        // SSL 配置
        if (Boolean.TRUE.equals(mailProperties.getSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", mailProperties.getPort());
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.debug", "false");

        return mailSender;
    }
}
