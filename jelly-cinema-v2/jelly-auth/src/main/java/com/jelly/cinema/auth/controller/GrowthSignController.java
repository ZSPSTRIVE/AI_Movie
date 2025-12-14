package com.jelly.cinema.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.jelly.cinema.auth.domain.vo.SignStatusVO;
import com.jelly.cinema.auth.service.SignService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户签到")
@RestController
@RequestMapping("/auth/growth/sign")
@RequiredArgsConstructor
@SaCheckLogin
public class GrowthSignController {

    private final SignService signService;

    @Operation(summary = "每日签到")
    @PostMapping("/checkin")
    public R<Boolean> checkin() {
        return R.ok(signService.checkin());
    }

    @Operation(summary = "获取签到状态")
    @GetMapping("/status")
    public R<SignStatusVO> getSignStatus() {
        return R.ok(signService.getSignStatus());
    }
}
