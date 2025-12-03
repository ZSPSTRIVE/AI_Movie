package com.jelly.cinema.admin.controller;

import com.jelly.cinema.admin.domain.dto.UserQueryDTO;
import com.jelly.cinema.admin.domain.entity.User;
import com.jelly.cinema.admin.service.AdminUserService;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/list")
    public R<PageResult<User>> list(UserQueryDTO query) {
        return R.ok(userService.list(query));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public R<User> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }

    @Operation(summary = "禁用/启用用户")
    @PostMapping("/status/{id}")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/reset-password/{id}")
    public R<Void> resetPassword(@PathVariable Long id, @RequestParam String newPassword) {
        userService.resetPassword(id, newPassword);
        return R.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }
}
