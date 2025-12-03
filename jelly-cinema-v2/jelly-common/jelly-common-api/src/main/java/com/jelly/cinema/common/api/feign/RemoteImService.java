package com.jelly.cinema.common.api.feign;

import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.api.domain.RemoteGroup;
import com.jelly.cinema.common.api.domain.RemoteGroupSimple;
import com.jelly.cinema.common.api.domain.RemoteMessage;
import com.jelly.cinema.common.api.feign.fallback.RemoteImFallback;
import com.jelly.cinema.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IM 服务 Feign 接口
 *
 * @author Jelly Cinema
 */
@FeignClient(value = "jelly-im", fallbackFactory = RemoteImFallback.class)
public interface RemoteImService {

    // ==================== 群组管理 ====================

    /**
     * 分页查询群组列表（管理端）
     */
    @GetMapping("/im/admin/groups")
    R<List<RemoteGroup>> getGroups(
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    );

    /**
     * 获取群组总数
     */
    @GetMapping("/im/admin/groups/count")
    R<Long> getGroupCount();

    /**
     * 获取活跃群组数（7天内有消息的群）
     */
    @GetMapping("/im/admin/groups/active-count")
    R<Integer> getActiveGroupCount();

    /**
     * 强制解散群组
     */
    @PostMapping("/im/admin/groups/{groupId}/dismiss")
    R<Void> dismissGroup(@PathVariable("groupId") Long groupId, @RequestParam("reason") String reason);

    // ==================== 消息管理 ====================

    /**
     * 获取群聊历史消息
     */
    @GetMapping("/im/admin/groups/{groupId}/messages")
    R<List<RemoteMessage>> getGroupMessages(
            @PathVariable("groupId") Long groupId,
            @RequestParam("pageNum") int pageNum,
            @RequestParam("pageSize") int pageSize
    );

    /**
     * 获取消息总数
     */
    @GetMapping("/im/admin/messages/count")
    R<Long> getMessageCount(@RequestParam(value = "groupId", required = false) Long groupId);

    // ==================== 用户关系 ====================

    /**
     * 获取用户好友列表
     */
    @GetMapping("/im/admin/users/{userId}/friends")
    R<List<RemoteFriend>> getUserFriends(@PathVariable("userId") Long userId);

    /**
     * 获取用户加入的群组列表
     */
    @GetMapping("/im/admin/users/{userId}/groups")
    R<List<RemoteGroupSimple>> getUserGroups(@PathVariable("userId") Long userId);
}
