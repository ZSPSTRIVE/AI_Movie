package com.jelly.cinema.im.websocket;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.domain.dto.MessageDTO;
import com.jelly.cinema.im.domain.vo.FriendVO;
import com.jelly.cinema.im.service.FriendService;
import com.jelly.cinema.im.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 消息处理器
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessageService messageService;
    private final FriendService friendService;
    
    public ChatWebSocketHandler(@Lazy MessageService messageService, @Lazy FriendService friendService) {
        this.messageService = messageService;
        this.friendService = friendService;
    }

    /**
     * 在线用户会话 Map: userId -> WebSocketSession
     */
    private static final Map<Long, WebSocketSession> ONLINE_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            ONLINE_SESSIONS.put(userId, session);
            log.info("用户上线: {}, 当前在线人数: {}, 在线用户: {}", userId, ONLINE_SESSIONS.size(), ONLINE_SESSIONS.keySet());
            // 通知好友该用户上线
            notifyFriendsOnlineStatus(userId, true);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = getUserId(session);
        if (userId == null) {
            return;
        }

        try {
            String payload = message.getPayload();
            log.debug("收到消息: userId={}, payload={}", userId, payload);

            // 解析消息
            MessageDTO dto = JSONUtil.toBean(payload, MessageDTO.class);
            
            // 处理消息
            messageService.sendMessage(userId, dto);
        } catch (Exception e) {
            log.error("处理消息失败: userId={}", userId, e);
            sendError(session, "消息处理失败: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        log.info("WebSocket connection closed: sessionId={}, userId={}, code={}, reason={}",
                session.getId(),
                userId,
                status != null ? status.getCode() : null,
                status != null ? status.getReason() : null);
        if (userId != null) {
            // 只有当前session是存储的session时才移除，避免新连接被旧连接关闭事件覆盖
            WebSocketSession storedSession = ONLINE_SESSIONS.get(userId);
            if (storedSession != null && storedSession.getId().equals(session.getId())) {
                ONLINE_SESSIONS.remove(userId);
                log.info("用户下线: {}, 当前在线人数: {}, 在线用户: {}", userId, ONLINE_SESSIONS.size(), ONLINE_SESSIONS.keySet());
                // 通知好友该用户下线
                notifyFriendsOnlineStatus(userId, false);
            } else {
                log.info("旧连接关闭，但用户已有新连接: userId={}", userId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long userId = getUserId(session);
        log.error("WebSocket 传输错误: userId={}", userId, exception);
    }

    /**
     * 发送消息给指定用户
     */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = ONLINE_SESSIONS.get(userId);
        log.info("推送消息: userId={}, 在线={}, sessionOpen={}", userId, session != null, session != null && session.isOpen());
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                log.info("消息推送成功: userId={}", userId);
            } catch (IOException e) {
                log.error("发送消息失败: userId={}", userId, e);
            }
        } else {
            log.warn("用户不在线，无法推送: userId={}", userId);
        }
    }

    /**
     * 发送消息给多个用户
     */
    public void sendToUsers(Iterable<Long> userIds, String message) {
        for (Long userId : userIds) {
            sendToUser(userId, message);
        }
    }

    /**
     * 判断用户是否在线
     */
    public boolean isOnline(Long userId) {
        WebSocketSession session = ONLINE_SESSIONS.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * 批量查询用户在线状态
     */
    public Map<Long, Boolean> getOnlineStatus(Iterable<Long> userIds) {
        Map<Long, Boolean> result = new ConcurrentHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, isOnline(userId));
        }
        return result;
    }

    /**
     * 获取所有在线用户ID
     */
    public java.util.Set<Long> getOnlineUserIds() {
        return ONLINE_SESSIONS.keySet();
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String error) {
        try {
            String json = JSONUtil.toJsonStr(Map.of("type", "error", "message", error));
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送错误消息失败", e);
        }
    }

    /**
     * 获取用户 ID
     */
    private Long getUserId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }

    /**
     * 通知好友该用户的在线状态变化
     */
    private void notifyFriendsOnlineStatus(Long userId, boolean online) {
        try {
            List<FriendVO> friends = friendService.getFriendList(userId);
            if (friends == null || friends.isEmpty()) {
                return;
            }
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "online");
            notification.put("userId", String.valueOf(userId));  // 转为字符串避免 JS 大数字精度丢失
            notification.put("online", online);
            String json = JSONUtil.toJsonStr(notification);
            
            for (FriendVO friend : friends) {
                // 只通知在线的好友
                if (isOnline(friend.getId())) {
                    sendToUser(friend.getId(), json);
                }
            }
            log.info("已通知 {} 位好友用户 {} 的在线状态变化: online={}", friends.size(), userId, online);
        } catch (Exception e) {
            log.error("通知好友在线状态失败: userId={}", userId, e);
        }
    }
}
