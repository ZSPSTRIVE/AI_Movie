package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.api.domain.RemoteGroup;
import com.jelly.cinema.common.api.domain.RemoteGroupSimple;
import com.jelly.cinema.common.api.domain.RemoteMessage;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.im.service.ImAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IM 管理端接口（供 Admin 服务 Feign 调用）
 *
 * @author Jelly Cinema
 */
@Tag(name = "IM管理端接口")
@RestController
@RequestMapping("/im/admin")
@RequiredArgsConstructor
public class ImAdminController {

    private final ImAdminService imAdminService;

    // ==================== 群组管理 ====================

    @Operation(summary = "分页查询群组列表")
    @GetMapping("/groups")
    public R<List<RemoteGroup>> getGroups(
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(imAdminService.getGroups(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取群组总数")
    @GetMapping("/groups/count")
    public R<Long> getGroupCount() {
        return R.ok(imAdminService.getGroupCount());
    }

    @Operation(summary = "获取活跃群组数")
    @GetMapping("/groups/active-count")
    public R<Integer> getActiveGroupCount() {
        return R.ok(imAdminService.getActiveGroupCount());
    }

    @Operation(summary = "强制解散群组")
    @PostMapping("/groups/{groupId}/dismiss")
    public R<Void> dismissGroup(@PathVariable Long groupId, @RequestParam String reason) {
        imAdminService.dismissGroup(groupId, reason);
        return R.ok();
    }

    // ==================== 消息管理 ====================

    @Operation(summary = "获取群聊历史消息")
    @GetMapping("/groups/{groupId}/messages")
    public R<List<RemoteMessage>> getGroupMessages(
            @PathVariable Long groupId,
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize) {
        return R.ok(imAdminService.getGroupMessages(groupId, pageNum, pageSize));
    }

    @Operation(summary = "获取消息总数")
    @GetMapping("/messages/count")
    public R<Long> getMessageCount(@RequestParam(value = "groupId", required = false) Long groupId) {
        return R.ok(imAdminService.getMessageCount(groupId));
    }

    // ==================== 用户关系 ====================

    @Operation(summary = "获取用户好友列表")
    @GetMapping("/users/{userId}/friends")
    public R<List<RemoteFriend>> getUserFriends(@PathVariable Long userId) {
        return R.ok(imAdminService.getUserFriends(userId));
    }

    @Operation(summary = "获取用户加入的群组")
    @GetMapping("/users/{userId}/groups")
    public R<List<RemoteGroupSimple>> getUserGroups(@PathVariable Long userId) {
        return R.ok(imAdminService.getUserGroups(userId));
    }
}
