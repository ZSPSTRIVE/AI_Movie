package com.jelly.cinema.common.captcha.controller;

import com.jelly.cinema.common.captcha.domain.CaptchaVO;
import com.jelly.cinema.common.captcha.domain.EmailCodeDTO;
import com.jelly.cinema.common.captcha.service.CaptchaService;
import com.jelly.cinema.common.captcha.service.EmailService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 验证码控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "验证码管理")
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;
    private final EmailService emailService;

    /**
     * 获取图片验证码
     */
    @Operation(summary = "获取图片验证码")
    @GetMapping("/image")
    public R<CaptchaVO> getImageCaptcha() {
        return R.ok(captchaService.generateCaptcha());
    }

    /**
     * 发送邮箱验证码
     */
    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email")
    public R<Void> sendEmailCode(@Valid @RequestBody EmailCodeDTO dto) {
        // 先验证图片验证码（防止滥发）
        if (dto.getCaptcha() != null && dto.getCaptchaKey() != null) {
            captchaService.checkCaptcha(dto.getCaptchaKey(), dto.getCaptcha());
        }
        
        // 发送邮箱验证码
        emailService.sendVerificationCode(dto.getEmail(), dto.getBusinessType());
        return R.ok();
    }
}
