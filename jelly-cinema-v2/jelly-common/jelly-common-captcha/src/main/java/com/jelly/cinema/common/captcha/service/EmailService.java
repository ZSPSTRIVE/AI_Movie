package com.jelly.cinema.common.captcha.service;

import cn.hutool.core.util.RandomUtil;
import com.jelly.cinema.common.captcha.config.MailProperties;
import com.jelly.cinema.common.captcha.constant.CaptchaConstants;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 邮件服务
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final RedisService redisService;

    /**
     * 发送邮箱验证码
     *
     * @param email        目标邮箱
     * @param businessType 业务类型
     */
    public void sendVerificationCode(String email, String businessType) {
        // 检查发送频率限制
        checkRateLimit(email);
        
        // 检查每日发送限制
        checkDailyLimit(email);
        
        // 生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);
        
        // 存储验证码到 Redis
        String codeKey = buildCodeKey(email, businessType);
        redisService.set(codeKey, code, 
                mailProperties.getCodeExpireSeconds(), TimeUnit.SECONDS);
        
        // 记录发送频率
        String rateLimitKey = CaptchaConstants.EMAIL_RATE_LIMIT_KEY + email;
        redisService.set(rateLimitKey, "1", 
                mailProperties.getSendIntervalSeconds(), TimeUnit.SECONDS);
        
        // 增加每日发送计数
        incrementDailyCount(email);
        
        // 同步发送邮件（便于调试，生产环境可改回异步）
        log.info("准备发送邮件: to={}, from={}, host={}", email, mailProperties.getFrom(), mailProperties.getHost());
        String subject = getEmailSubject(businessType);
        String content = buildEmailContent(code, businessType);
        sendHtmlEmail(email, subject, content);
        
        log.info("发送邮箱验证码: email={}, businessType={}, code={}", email, businessType, code);
    }

    /**
     * 验证邮箱验证码
     *
     * @param email        邮箱
     * @param code         验证码
     * @param businessType 业务类型
     * @return 验证是否通过
     */
    public boolean validateCode(String email, String code, String businessType) {
        if (email == null || code == null || businessType == null) {
            return false;
        }
        
        // 检查错误次数
        String errorCountKey = CaptchaConstants.CODE_ERROR_COUNT_KEY + email + ":" + businessType;
        Integer errorCount = redisService.get(errorCountKey);
        if (errorCount != null && errorCount >= CaptchaConstants.MAX_ERROR_COUNT) {
            throw new ServiceException("验证码错误次数过多，请稍后再试");
        }
        
        String codeKey = buildCodeKey(email, businessType);
        String storedCode = redisService.get(codeKey);
        
        if (storedCode == null) {
            log.debug("验证码已过期或不存在: email={}, businessType={}", email, businessType);
            return false;
        }
        
        if (!storedCode.equals(code)) {
            // 记录错误次数
            if (errorCount == null) {
                redisService.set(errorCountKey, 1, CaptchaConstants.ERROR_LOCK_TIME, TimeUnit.SECONDS);
            } else {
                redisService.increment(errorCountKey);
            }
            log.debug("验证码错误: email={}, input={}, stored={}", email, code, storedCode);
            return false;
        }
        
        // 验证成功，删除验证码和错误计数
        redisService.delete(codeKey);
        redisService.delete(errorCountKey);
        
        log.debug("验证码验证成功: email={}, businessType={}", email, businessType);
        return true;
    }

    /**
     * 验证邮箱验证码（验证失败抛出异常）
     *
     * @param email        邮箱
     * @param code         验证码
     * @param businessType 业务类型
     */
    public void checkCode(String email, String code, String businessType) {
        if (!validateCode(email, code, businessType)) {
            throw new ServiceException("邮箱验证码错误或已过期");
        }
    }

    /**
     * 检查发送频率限制
     */
    private void checkRateLimit(String email) {
        String rateLimitKey = CaptchaConstants.EMAIL_RATE_LIMIT_KEY + email;
        if (Boolean.TRUE.equals(redisService.hasKey(rateLimitKey))) {
            Long ttl = redisService.getExpire(rateLimitKey);
            throw new ServiceException("发送太频繁，请 " + ttl + " 秒后重试");
        }
    }

    /**
     * 检查每日发送限制
     */
    private void checkDailyLimit(String email) {
        String dailyKey = buildDailyCountKey(email);
        Integer count = redisService.get(dailyKey);
        if (count != null && count >= mailProperties.getDailyLimit()) {
            throw new ServiceException("今日发送次数已达上限，请明天再试");
        }
    }

    /**
     * 增加每日发送计数
     */
    private void incrementDailyCount(String email) {
        String dailyKey = buildDailyCountKey(email);
        if (Boolean.TRUE.equals(redisService.hasKey(dailyKey))) {
            redisService.increment(dailyKey);
        } else {
            // 设置到当天结束时过期
            redisService.set(dailyKey, 1, getSecondsUntilMidnight(), TimeUnit.SECONDS);
        }
    }

    /**
     * 构建验证码 Redis Key
     */
    private String buildCodeKey(String email, String businessType) {
        return CaptchaConstants.EMAIL_CODE_KEY + businessType + ":" + email;
    }

    /**
     * 构建每日计数 Redis Key
     */
    private String buildDailyCountKey(String email) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return CaptchaConstants.EMAIL_DAILY_COUNT_KEY + today + ":" + email;
    }

    /**
     * 获取到午夜的秒数
     */
    private long getSecondsUntilMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }

    /**
     * 异步发送验证码邮件
     */
    @Async
    public void sendCodeEmailAsync(String to, String code, String businessType) {
        String subject = getEmailSubject(businessType);
        String content = buildEmailContent(code, businessType);
        sendHtmlEmail(to, subject, content);
    }

    /**
     * 发送 HTML 邮件
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(mailProperties.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("邮件发送成功: to={}, subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}, error={}", to, subject, e.getMessage());
            throw new ServiceException("邮件发送失败，请稍后重试");
        }
    }

    /**
     * 获取邮件主题
     */
    private String getEmailSubject(String businessType) {
        return switch (businessType) {
            case CaptchaConstants.BusinessType.REGISTER -> "【果冻影院】注册验证码";
            case CaptchaConstants.BusinessType.LOGIN -> "【果冻影院】登录验证码";
            case CaptchaConstants.BusinessType.RESET_PASSWORD -> "【果冻影院】找回密码验证码";
            case CaptchaConstants.BusinessType.BIND_EMAIL -> "【果冻影院】绑定邮箱验证码";
            default -> "【果冻影院】验证码";
        };
    }

    /**
     * 构建邮件内容
     */
    private String buildEmailContent(String code, String businessType) {
        String action = switch (businessType) {
            case CaptchaConstants.BusinessType.REGISTER -> "注册账号";
            case CaptchaConstants.BusinessType.LOGIN -> "登录账号";
            case CaptchaConstants.BusinessType.RESET_PASSWORD -> "找回密码";
            case CaptchaConstants.BusinessType.BIND_EMAIL -> "绑定邮箱";
            default -> "验证操作";
        };
        
        int expireMinutes = mailProperties.getCodeExpireSeconds() / 60;
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Microsoft YaHei', Arial, sans-serif; background-color: #f5f5f5; padding: 20px; }
                    .container { max-width: 600px; margin: 0 auto; background: #fff; border-radius: 10px; padding: 30px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .header { text-align: center; margin-bottom: 30px; }
                    .header h1 { color: #4CAF50; margin: 0; font-size: 28px; }
                    .content { color: #333; line-height: 1.8; }
                    .code-box { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #fff; font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .tips { color: #999; font-size: 14px; margin-top: 20px; padding: 15px; background: #f9f9f9; border-radius: 5px; }
                    .footer { text-align: center; color: #999; font-size: 12px; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🍮 果冻影院</h1>
                    </div>
                    <div class="content">
                        <p>您好！</p>
                        <p>您正在进行 <strong>%s</strong> 操作，验证码为：</p>
                        <div class="code-box">%s</div>
                        <div class="tips">
                            <p>⏰ 验证码有效期为 <strong>%d 分钟</strong>，请尽快使用。</p>
                            <p>🔒 如非本人操作，请忽略此邮件。</p>
                            <p>⚠️ 请勿将验证码告知他人，以防账号被盗。</p>
                        </div>
                    </div>
                    <div class="footer">
                        <p>此邮件由系统自动发送，请勿回复</p>
                        <p>© 2024 果冻影院 - 影视 + 社交 + AI 一体化平台</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(action, code, expireMinutes);
    }
}
