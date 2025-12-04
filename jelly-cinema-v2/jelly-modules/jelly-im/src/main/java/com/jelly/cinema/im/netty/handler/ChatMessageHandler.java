package com.jelly.cinema.im.netty.handler;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.netty.model.ChatMessageDTO;
import com.jelly.cinema.im.netty.mq.MessageProducer;
import com.jelly.cinema.im.netty.protocol.MessageProtocol;
import com.jelly.cinema.im.netty.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 聊天消息处理器
 * 
 * 处理流程：
 * 1. 接收客户端消息
 * 2. 消息校验
 * 3. 发送 ACK 给发送方
 * 4. 写入 MQ 进行异步处理
 * 5. 路由消息到接收方
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ChannelHandler.Sharable
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class ChatMessageHandler extends SimpleChannelInboundHandler<MessageProtocol> {

    private final SessionManager sessionManager;
    private final MessageProducer messageProducer;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MessageProtocol protocol) throws Exception {
        byte messageType = protocol.getMessageType();

        switch (messageType) {
            case MessageProtocol.MessageType.AUTH_REQUEST:
                handleAuth(ctx, protocol);
                break;
            case MessageProtocol.MessageType.CHAT_MESSAGE:
                handleChatMessage(ctx, protocol);
                break;
            case MessageProtocol.MessageType.GROUP_MESSAGE:
                handleGroupMessage(ctx, protocol);
                break;
            case MessageProtocol.MessageType.READ_RECEIPT:
                handleReadReceipt(ctx, protocol);
                break;
            case MessageProtocol.MessageType.CHAT_ACK:
                handleChatAck(ctx, protocol);
                break;
            default:
                log.warn("未知消息类型: {}", messageType);
        }
    }

    /**
     * 处理认证请求
     */
    private void handleAuth(ChannelHandlerContext ctx, MessageProtocol protocol) {
        try {
            // 解析认证信息（简化示例，实际应验证 Token）
            String content = new String(protocol.getContent(), StandardCharsets.UTF_8);
            // 假设内容格式为 {"userId": 123, "token": "xxx"}
            Long userId = JSONUtil.parseObj(content).getLong("userId");

            if (userId != null) {
                // 绑定用户会话
                sessionManager.bind(userId, ctx.channel());

                // 发送认证成功响应
                MessageProtocol response = MessageProtocol.builder()
                        .messageType(MessageProtocol.MessageType.AUTH_RESPONSE)
                        .messageId(protocol.getMessageId())
                        .status(MessageProtocol.MessageStatus.SUCCESS)
                        .serializerType(MessageProtocol.SerializerType.JSON)
                        .content("{\"code\":0,\"message\":\"认证成功\"}".getBytes(StandardCharsets.UTF_8))
                        .build();

                ctx.writeAndFlush(response);
                log.info("用户认证成功: userId={}", userId);

                // 推送离线消息
                pushOfflineMessages(userId);
            } else {
                sendAuthFailResponse(ctx, protocol, "用户ID不能为空");
            }
        } catch (Exception e) {
            log.error("认证失败", e);
            sendAuthFailResponse(ctx, protocol, "认证失败: " + e.getMessage());
        }
    }

    /**
     * 发送认证失败响应
     */
    private void sendAuthFailResponse(ChannelHandlerContext ctx, MessageProtocol protocol, String message) {
        MessageProtocol response = MessageProtocol.builder()
                .messageType(MessageProtocol.MessageType.AUTH_RESPONSE)
                .messageId(protocol.getMessageId())
                .status(MessageProtocol.MessageStatus.FAIL)
                .serializerType(MessageProtocol.SerializerType.JSON)
                .content(("{\"code\":1,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8))
                .build();
        ctx.writeAndFlush(response);
    }

    /**
     * 处理私聊消息
     */
    private void handleChatMessage(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Long senderId = sessionManager.getUserId(ctx.channel());
        if (senderId == null) {
            log.warn("未认证用户发送消息，拒绝处理");
            return;
        }

        try {
            // 1. 解析消息内容
            String content = new String(protocol.getContent(), StandardCharsets.UTF_8);
            ChatMessageDTO chatMessage = JSONUtil.toBean(content, ChatMessageDTO.class);
            chatMessage.setSenderId(senderId);
            chatMessage.setMessageId(protocol.getMessageId());
            chatMessage.setTimestamp(System.currentTimeMillis());

            log.info("收到私聊消息: from={}, to={}, msgId={}", 
                    senderId, chatMessage.getReceiverId(), protocol.getMessageId());

            // 2. 发送 ACK 给发送方
            sendAck(ctx, protocol.getMessageId(), MessageProtocol.MessageStatus.DELIVERED);

            // 3. 写入 MQ 进行异步持久化和处理
            messageProducer.sendChatMessage(chatMessage);

            // 4. 尝试直接推送给接收方（如果在线）
            boolean delivered = routeToReceiver(chatMessage);
            if (!delivered) {
                log.info("接收方不在线，消息已进入离线队列: to={}, msgId={}", 
                        chatMessage.getReceiverId(), protocol.getMessageId());
            }

        } catch (Exception e) {
            log.error("处理私聊消息失败", e);
            sendAck(ctx, protocol.getMessageId(), MessageProtocol.MessageStatus.FAIL);
        }
    }

    /**
     * 处理群聊消息
     */
    private void handleGroupMessage(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Long senderId = sessionManager.getUserId(ctx.channel());
        if (senderId == null) {
            log.warn("未认证用户发送消息，拒绝处理");
            return;
        }

        try {
            // 1. 解析消息内容
            String content = new String(protocol.getContent(), StandardCharsets.UTF_8);
            ChatMessageDTO chatMessage = JSONUtil.toBean(content, ChatMessageDTO.class);
            chatMessage.setSenderId(senderId);
            chatMessage.setMessageId(protocol.getMessageId());
            chatMessage.setTimestamp(System.currentTimeMillis());
            chatMessage.setSessionType(ChatMessageDTO.SessionType.GROUP);

            log.info("收到群聊消息: from={}, groupId={}, msgId={}", 
                    senderId, chatMessage.getReceiverId(), protocol.getMessageId());

            // 2. 发送 ACK 给发送方
            sendAck(ctx, protocol.getMessageId(), MessageProtocol.MessageStatus.DELIVERED);

            // 3. 写入 MQ 进行群消息扩散
            messageProducer.sendGroupMessage(chatMessage);

        } catch (Exception e) {
            log.error("处理群聊消息失败", e);
            sendAck(ctx, protocol.getMessageId(), MessageProtocol.MessageStatus.FAIL);
        }
    }

    /**
     * 处理已读回执
     */
    private void handleReadReceipt(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Long userId = sessionManager.getUserId(ctx.channel());
        if (userId == null) return;

        try {
            String content = new String(protocol.getContent(), StandardCharsets.UTF_8);
            // 解析已读消息 ID 列表，更新已读状态
            log.debug("收到已读回执: userId={}, content={}", userId, content);
            
            // 发送到 MQ 异步处理已读状态
            messageProducer.sendReadReceipt(userId, content);
        } catch (Exception e) {
            log.error("处理已读回执失败", e);
        }
    }

    /**
     * 处理消息 ACK
     */
    private void handleChatAck(ChannelHandlerContext ctx, MessageProtocol protocol) {
        Long userId = sessionManager.getUserId(ctx.channel());
        log.debug("收到消息 ACK: userId={}, msgId={}", userId, protocol.getMessageId());
        
        // 更新消息投递状态
        messageProducer.confirmMessageDelivered(protocol.getMessageId());
    }

    /**
     * 发送 ACK 响应
     */
    private void sendAck(ChannelHandlerContext ctx, long messageId, byte status) {
        MessageProtocol ack = MessageProtocol.builder()
                .messageType(MessageProtocol.MessageType.CHAT_ACK)
                .messageId(messageId)
                .status(status)
                .serializerType(MessageProtocol.SerializerType.JSON)
                .build();
        ctx.writeAndFlush(ack);
    }

    /**
     * 路由消息到接收方
     */
    private boolean routeToReceiver(ChatMessageDTO chatMessage) {
        Long receiverId = chatMessage.getReceiverId();

        // 先检查本地是否在线
        if (sessionManager.isOnlineLocal(receiverId)) {
            return pushToLocalUser(receiverId, chatMessage);
        }

        // 检查是否在其他服务器（分布式场景）
        if (sessionManager.isOnlineGlobal(receiverId)) {
            // 通过 MQ 转发到其他服务器
            messageProducer.routeToOtherServer(chatMessage);
            return true;
        }

        // 用户离线，存入离线消息队列
        messageProducer.saveOfflineMessage(chatMessage);
        return false;
    }

    /**
     * 推送消息给本地用户
     */
    private boolean pushToLocalUser(Long userId, ChatMessageDTO chatMessage) {
        MessageProtocol message = MessageProtocol.builder()
                .messageType(MessageProtocol.MessageType.CHAT_MESSAGE)
                .messageId(chatMessage.getMessageId())
                .status(MessageProtocol.MessageStatus.SUCCESS)
                .serializerType(MessageProtocol.SerializerType.JSON)
                .content(JSONUtil.toJsonStr(chatMessage).getBytes(StandardCharsets.UTF_8))
                .build();

        return sessionManager.sendToUser(userId, message);
    }

    /**
     * 推送离线消息
     */
    private void pushOfflineMessages(Long userId) {
        // 从 MQ/Redis 获取离线消息并推送
        messageProducer.pushOfflineMessages(userId);
    }
}
