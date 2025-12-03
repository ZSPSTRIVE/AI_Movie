package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.dto.UserSettingDTO;
import com.jelly.cinema.im.domain.entity.UserSetting;
import com.jelly.cinema.im.service.UserSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户设置控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "用户设置")
@RestController
@RequestMapping("/im/setting")
@RequiredArgsConstructor
public class UserSettingController {

    private final UserSettingService userSettingService;

    @Operation(summary = "获取用户设置")
    @GetMapping
    public R<UserSetting> getUserSetting() {
        Long userId = LoginHelper.getUserId();
        return R.ok(userSettingService.getUserSetting(userId));
    }

    @Operation(summary = "更新用户设置")
    @PutMapping
    public R<Void> updateUserSetting(@RequestBody UserSettingDTO dto) {
        Long userId = LoginHelper.getUserId();
        userSettingService.updateUserSetting(userId, dto);
        return R.ok();
    }
}
