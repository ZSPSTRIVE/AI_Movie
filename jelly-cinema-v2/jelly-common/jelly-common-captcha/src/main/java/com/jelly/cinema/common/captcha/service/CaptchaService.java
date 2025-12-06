package com.jelly.cinema.common.captcha.service;

import cn.hutool.core.util.IdUtil;
import com.google.code.kaptcha.Producer;
import com.jelly.cinema.common.captcha.constant.CaptchaConstants;
import com.jelly.cinema.common.captcha.domain.CaptchaVO;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 图片验证码服务
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final Producer kaptchaProducer;
    private final RedisService redisService;

    /**
     * 生成图片验证码
     *
     * @return 验证码响应
     */
    public CaptchaVO generateCaptcha() {
        // 生成验证码文本
        String captchaText = kaptchaProducer.createText();
        
        // 生成验证码图片
        BufferedImage image = kaptchaProducer.createImage(captchaText);
        
        // 转换为 Base64
        String base64Image;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("生成验证码图片失败", e);
            throw new ServiceException("生成验证码失败");
        }
        
        // 生成唯一标识
        String captchaKey = IdUtil.fastSimpleUUID();
        
        // 存入 Redis（忽略大小写，统一转小写）
        String redisKey = CaptchaConstants.IMAGE_CAPTCHA_KEY + captchaKey;
        redisService.set(redisKey, captchaText.toLowerCase(), 
                CaptchaConstants.IMAGE_CAPTCHA_EXPIRE, TimeUnit.SECONDS);
        
        log.debug("生成图片验证码: key={}, code={}", captchaKey, captchaText);
        
        return CaptchaVO.builder()
                .captchaKey(captchaKey)
                .captchaImage(base64Image)
                .expireSeconds(CaptchaConstants.IMAGE_CAPTCHA_EXPIRE)
                .build();
    }

    /**
     * 验证图片验证码
     *
     * @param captchaKey  验证码 Key
     * @param captchaCode 用户输入的验证码
     * @return 验证是否通过
     */
    public boolean validateCaptcha(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            return false;
        }
        
        String redisKey = CaptchaConstants.IMAGE_CAPTCHA_KEY + captchaKey;
        String storedCode = redisService.get(redisKey);
        
        if (storedCode == null) {
            log.debug("验证码已过期或不存在: key={}", captchaKey);
            return false;
        }
        
        // 验证后立即删除，防止重复使用
        redisService.delete(redisKey);
        
        // 忽略大小写比较
        boolean valid = storedCode.equalsIgnoreCase(captchaCode);
        log.debug("验证图片验证码: key={}, input={}, stored={}, valid={}", 
                captchaKey, captchaCode, storedCode, valid);
        
        return valid;
    }

    /**
     * 验证图片验证码（验证失败抛出异常）
     *
     * @param captchaKey  验证码 Key
     * @param captchaCode 用户输入的验证码
     */
    public void checkCaptcha(String captchaKey, String captchaCode) {
        if (!validateCaptcha(captchaKey, captchaCode)) {
            throw new ServiceException("验证码错误或已过期");
        }
    }
}
