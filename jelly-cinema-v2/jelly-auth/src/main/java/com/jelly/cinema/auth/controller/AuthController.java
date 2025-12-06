package com.jelly.cinema.auth.controller;

import com.jelly.cinema.auth.domain.dto.LoginDTO;
import com.jelly.cinema.auth.domain.dto.RegisterDTO;
import com.jelly.cinema.auth.domain.vo.LoginVO;
import com.jelly.cinema.auth.service.AuthService;
import com.jelly.cinema.common.captcha.constant.CaptchaConstants;
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

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final EmailService emailService;

    /**
     * 获取图片验证码
     */
    @Operation(summary = "获取图片验证码")
    @GetMapping("/captcha")
    public R<CaptchaVO> getCaptcha() {
        return R.ok(captchaService.generateCaptcha());
    }

    /**
     * 发送邮箱验证码
     */
    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/email/code")
    public R<Void> sendEmailCode(@Valid @RequestBody EmailCodeDTO dto) {
        // 先验证图片验证码（防止滥发）
        if (dto.getCaptcha() != null && dto.getCaptchaKey() != null) {
            captchaService.checkCaptcha(dto.getCaptchaKey(), dto.getCaptcha());
        }
        
        // 发送邮箱验证码
        emailService.sendVerificationCode(dto.getEmail(), dto.getBusinessType());
        return R.ok();
    }

    /**
     * 检查是否需要邮箱二次验证
     */
    @Operation(summary = "检查是否需要邮箱验证")
    @GetMapping("/check/email-verify")
    public R<Map<String, Object>> checkEmailVerify(@RequestParam String username) {
        Map<String, Object> result = new HashMap<>();
        boolean needVerify = authService.needEmailVerification(username);
        result.put("needEmailVerify", needVerify);
        
        if (needVerify) {
            String maskedEmail = authService.getMaskedEmail(username);
            result.put("maskedEmail", maskedEmail);
        }
        
        return R.ok(result);
    }

    /**
     * 发送登录验证邮箱验证码
     */
    @Operation(summary = "发送登录验证邮箱验证码")
    @PostMapping("/login/email-code")
    public R<Void> sendLoginEmailCode(@RequestParam String username,
                                       @RequestParam String captcha,
                                       @RequestParam String captchaKey) {
        // 验证图片验证码
        captchaService.checkCaptcha(captchaKey, captcha);
        
        // 获取用户完整邮箱
        String email = authService.getFullEmail(username);
        if (email == null) {
            return R.fail("该用户未绑定邮箱");
        }
        
        // 发送验证码
        emailService.sendVerificationCode(email, CaptchaConstants.BusinessType.LOGIN);
        return R.ok();
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return R.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
