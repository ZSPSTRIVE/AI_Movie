package com.jelly.cinema.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.UserDetailVO;
import com.jelly.cinema.admin.domain.vo.UserListVO;
import com.jelly.cinema.admin.service.UserManageService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/admin/user/manage")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageService userManageService;

    @Operation(summary = "分页查询用户列表")
    @GetMapping("/list")
    public R<Page<UserListVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return R.ok(userManageService.page(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/detail/{userId}")
    public R<UserDetailVO> getDetail(@PathVariable Long userId) {
        return R.ok(userManageService.getDetail(userId));
    }

    @Operation(summary = "封禁用户")
    @PostMapping("/ban")
    public R<Void> ban(@RequestBody BanDTO dto) {
        userManageService.banUser(dto.getUserId(), dto.getDuration(), dto.getReason());
        return R.ok();
    }

    @Operation(summary = "解封用户")
    @PostMapping("/unban/{userId}")
    public R<Void> unban(@PathVariable Long userId) {
        userManageService.unbanUser(userId);
        return R.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/reset-password/{userId}")
    public R<String> resetPassword(@PathVariable Long userId) {
        String newPassword = userManageService.resetPassword(userId);
        return R.ok(newPassword, "密码已重置");
    }

    @Operation(summary = "强制下线")
    @PostMapping("/force-logout/{userId}")
    public R<Void> forceLogout(@PathVariable Long userId) {
        userManageService.forceLogout(userId);
        return R.ok();
    }

    @Data
    public static class BanDTO {
        private Long userId;
        private Integer duration; // 小时，0=永久
        private String reason;
    }

    @Operation(summary = "创建用户")
    @PostMapping("/create")
    public R<Long> create(@RequestBody CreateUserDTO dto) {
        return R.ok(userManageService.createUser(dto.getUsername(), dto.getPassword(), dto.getNickname(), dto.getRole()));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/update/{userId}")
    public R<Void> update(@PathVariable Long userId, @RequestBody UpdateUserDTO dto) {
        userManageService.updateUser(userId, dto.getNickname(), dto.getEmail(), dto.getPhone(), dto.getRole());
        return R.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    public R<Void> delete(@PathVariable Long userId) {
        userManageService.deleteUser(userId);
        return R.ok();
    }

    @Data
    public static class CreateUserDTO {
        private String username;
        private String password;
        private String nickname;
        private String role;
    }

    @Data
    public static class UpdateUserDTO {
        private String nickname;
        private String email;
        private String phone;
        private String role;
    }
}
