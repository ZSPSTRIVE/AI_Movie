package com.jelly.cinema.auth.controller;

import com.jelly.cinema.auth.domain.vo.UserVO;
import com.jelly.cinema.auth.service.UserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.domain.model.LoginUser;
import com.jelly.cinema.common.security.utils.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户信息控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "用户信息")
@RestController
@RequestMapping("/auth/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public R<LoginUser> getUserInfo() {
        return R.ok(LoginHelper.getLoginUser());
    }

    @Operation(summary = "根据ID获取用户信息（Feign调用）")
    @GetMapping("/info/{id}")
    public R<UserVO> getUserById(@PathVariable Long id) {
        return R.ok(userService.getUserById(id));
    }

    @Operation(summary = "批量获取用户信息（Feign调用）")
    @GetMapping("/batch")
    public R<List<UserVO>> getUsersByIds(@RequestParam List<Long> ids) {
        return R.ok(userService.getUsersByIds(ids));
    }

    @Operation(summary = "更新用户资料")
    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody UserVO userVO) {
        userService.updateProfile(userVO);
        return R.ok();
    }

    @Operation(summary = "更新头像")
    @PutMapping("/avatar")
    public R<String> updateAvatar(@RequestParam String avatar) {
        return R.ok(userService.updateAvatar(avatar));
    }

    @Operation(summary = "搜索用户（Feign调用）")
    @GetMapping("/search")
    public R<List<UserVO>> searchUsers(@RequestParam String keyword) {
        return R.ok(userService.searchUsers(keyword));
    }
}
