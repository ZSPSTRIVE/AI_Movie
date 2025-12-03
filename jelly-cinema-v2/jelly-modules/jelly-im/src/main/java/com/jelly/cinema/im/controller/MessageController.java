package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.vo.MessageVO;
import com.jelly.cinema.im.domain.vo.SessionVO;
import com.jelly.cinema.im.service.ImAdminService;
import com.jelly.cinema.im.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "IM 消息管理")
@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ImAdminService imAdminService;
    private final com.jelly.cinema.im.websocket.ChatWebSocketHandler webSocketHandler;

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public R<List<SessionVO>> getSessions() {
        Long userId = LoginHelper.getUserId();
        return R.ok(messageService.getSessionList(userId));
    }

    @Operation(summary = "获取历史消息")
    @GetMapping("/history/{sessionId}")
    public R<PageResult<MessageVO>> getHistory(
            @PathVariable String sessionId,
            PageQuery query) {
        return R.ok(messageService.getHistory(sessionId, query));
    }

    @Operation(summary = "撤回消息")
    @PostMapping("/recall/{messageId}")
    public R<Void> recall(@PathVariable Long messageId) {
        Long userId = LoginHelper.getUserId();
        messageService.recallMessage(userId, messageId);
        return R.ok();
    }

    @Operation(summary = "获取当前用户好友列表")
    @GetMapping("/friends")
    public R<List<RemoteFriend>> getFriends() {
        Long userId = LoginHelper.getUserId();
        return R.ok(imAdminService.getUserFriends(userId));
    }

    @Operation(summary = "删除会话")
    @PostMapping("/session/delete/{sessionId}")
    public R<Void> deleteSession(@PathVariable String sessionId,
                                 @RequestParam(defaultValue = "false") Boolean keepMessages) {
        Long userId = LoginHelper.getUserId();
        messageService.deleteSession(userId, sessionId, keepMessages);
        return R.ok();
    }

    @Operation(summary = "删除单条消息")
    @PostMapping("/message/delete/{messageId}")
    public R<Void> deleteMessage(@PathVariable Long messageId) {
        Long userId = LoginHelper.getUserId();
        messageService.deleteMessage(userId, messageId);
        return R.ok();
    }

    @Operation(summary = "清空会话消息")
    @PostMapping("/session/clear/{sessionId}")
    public R<Void> clearMessages(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        messageService.clearMessages(userId, sessionId);
        return R.ok();
    }

    @Operation(summary = "标记消息已读")
    @PostMapping("/session/read/{sessionId}")
    public R<Void> markAsRead(@PathVariable String sessionId) {
        Long userId = LoginHelper.getUserId();
        messageService.markAsRead(userId, sessionId);
        return R.ok();
    }

    @Operation(summary = "查询用户在线状态")
    @GetMapping("/online/{userId}")
    public R<Boolean> isOnline(@PathVariable Long userId) {
        return R.ok(webSocketHandler.isOnline(userId));
    }

    @Operation(summary = "批量查询用户在线状态")
    @PostMapping("/online/batch")
    public R<Map<Long, Boolean>> getOnlineStatus(@RequestBody List<Long> userIds) {
        return R.ok(webSocketHandler.getOnlineStatus(userIds));
    }
}
