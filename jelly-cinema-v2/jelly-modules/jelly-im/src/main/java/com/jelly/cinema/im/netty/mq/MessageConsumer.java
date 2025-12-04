package com.jelly.cinema.im.netty.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.netty.model.ChatMessageDTO;
import com.jelly.cinema.im.netty.protocol.MessageProtocol;
import com.jelly.cinema.im.netty.session.SessionManager;
import com.jelly.cinema.im.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 消息消费者
 * 
 * 负责：
 * 1. 消费 MQ 消息
 * 2. 持久化到数据库
 * 3. 推送消息给接收方
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component("nettyMessageConsumer")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class MessageConsumer {

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final MessageProducer messageProducer;

    /**
     * 私聊消息消费者
     */
    @Component
    @ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
    @RocketMQMessageListener(
            topic = "CHAT_MESSAGE_TOPIC",
            consumerGroup = "chat-message-consumer-group",
            consumeMode = ConsumeMode.ORDERLY
    )
    @RequiredArgsConstructor
    public static class NettyChatMessageConsumer implements RocketMQListener<ChatMessageDTO> {

        private final MessageService messageService;
        private final SessionManager sessionManager;
        private final MessageProducer messageProducer;

        @Override
        public void onMessage(ChatMessageDTO chatMessage) {
            log.info("消费私聊消息: msgId={}, from={}, to={}", 
                    chatMessage.getMessageId(), chatMessage.getSenderId(), chatMessage.getReceiverId());

            try {
                // 1. 持久化消息
                ChatMessage entity = convertToEntity(chatMessage);
                messageService.saveMessage(entity);

                // 2. 尝试推送给接收方
                boolean delivered = pushToReceiver(chatMessage);

                // 3. 如果接收方不在线，保存离线消息
                if (!delivered) {
                    messageProducer.saveOfflineMessage(chatMessage);
                }

                log.info("私聊消息处理完成: msgId={}, delivered={}", 
                        chatMessage.getMessageId(), delivered);

            } catch (Exception e) {
                log.error("处理私聊消息失败: msgId={}", chatMessage.getMessageId(), e);
                throw new RuntimeException("处理私聊消息失败", e);
            }
        }

        private ChatMessage convertToEntity(ChatMessageDTO dto) {
            ChatMessage entity = new ChatMessage();
            entity.setId(dto.getMessageId());
            entity.setSenderId(dto.getSenderId());
            entity.setReceiverId(dto.getReceiverId());
            entity.setContent(dto.getContent());
            entity.setContentType(dto.getContentType());
            entity.setSessionType(dto.getSessionType());
            entity.setCreateTime(LocalDateTime.now());
            return entity;
        }

        private boolean pushToReceiver(ChatMessageDTO chatMessage) {
            Long receiverId = chatMessage.getReceiverId();

            if (sessionManager.isOnlineLocal(receiverId)) {
                MessageProtocol message = MessageProtocol.builder()
                        .messageType(MessageProtocol.MessageType.CHAT_MESSAGE)
                        .messageId(chatMessage.getMessageId())
                        .status(MessageProtocol.MessageStatus.SUCCESS)
                        .serializerType(MessageProtocol.SerializerType.JSON)
                        .content(JSONUtil.toJsonStr(chatMessage).getBytes(StandardCharsets.UTF_8))
                        .build();

                return sessionManager.sendToUser(receiverId, message);
            }

            return false;
        }
    }

    /**
     * 群聊消息消费者
     */
    @Component
    @ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
    @RocketMQMessageListener(
            topic = "GROUP_MESSAGE_TOPIC",
            consumerGroup = "group-message-consumer-group",
            consumeMode = ConsumeMode.ORDERLY
    )
    @RequiredArgsConstructor
    public static class NettyGroupMessageConsumer implements RocketMQListener<ChatMessageDTO> {

        private final MessageService messageService;
        private final SessionManager sessionManager;
        private final MessageProducer messageProducer;

        @Override
        public void onMessage(ChatMessageDTO chatMessage) {
            log.info("消费群聊消息: msgId={}, from={}, groupId={}", 
                    chatMessage.getMessageId(), chatMessage.getSenderId(), chatMessage.getReceiverId());

            try {
                // 1. 持久化消息
                ChatMessage entity = new ChatMessage();
                entity.setId(chatMessage.getMessageId());
                entity.setSenderId(chatMessage.getSenderId());
                entity.setReceiverId(chatMessage.getReceiverId()); // 群ID
                entity.setContent(chatMessage.getContent());
                entity.setContentType(chatMessage.getContentType());
                entity.setSessionType(ChatMessageDTO.SessionType.GROUP);
                entity.setCreateTime(LocalDateTime.now());
                messageService.saveMessage(entity);

                // 2. 获取群成员列表并推送
                // 实际应从数据库获取群成员
                // List<Long> memberIds = groupService.getGroupMemberIds(chatMessage.getReceiverId());
                // for (Long memberId : memberIds) {
                //     if (!memberId.equals(chatMessage.getSenderId())) {
                //         pushToMember(memberId, chatMessage);
                //     }
                // }

                log.info("群聊消息处理完成: msgId={}", chatMessage.getMessageId());

            } catch (Exception e) {
                log.error("处理群聊消息失败: msgId={}", chatMessage.getMessageId(), e);
                throw new RuntimeException("处理群聊消息失败", e);
            }
        }
    }

    /**
     * 消息路由消费者（用于分布式场景）
     */
    @Component
    @ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
    @RocketMQMessageListener(
            topic = "MESSAGE_ROUTE_TOPIC",
            consumerGroup = "message-route-consumer-group"
    )
    @RequiredArgsConstructor
    public static class NettyMessageRouteConsumer implements RocketMQListener<ChatMessageDTO> {

        private final SessionManager sessionManager;

        @Override
        public void onMessage(ChatMessageDTO chatMessage) {
            Long receiverId = chatMessage.getReceiverId();

            log.info("消费路由消息: msgId={}, to={}", chatMessage.getMessageId(), receiverId);

            // 检查接收方是否在本节点
            if (sessionManager.isOnlineLocal(receiverId)) {
                MessageProtocol message = MessageProtocol.builder()
                        .messageType(MessageProtocol.MessageType.CHAT_MESSAGE)
                        .messageId(chatMessage.getMessageId())
                        .status(MessageProtocol.MessageStatus.SUCCESS)
                        .serializerType(MessageProtocol.SerializerType.JSON)
                        .content(JSONUtil.toJsonStr(chatMessage).getBytes(StandardCharsets.UTF_8))
                        .build();

                sessionManager.sendToUser(receiverId, message);
                log.info("路由消息推送成功: msgId={}, to={}", chatMessage.getMessageId(), receiverId);
            }
        }
    }

    /**
     * 已读回执消费者
     */
    @Component
    @ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
    @RocketMQMessageListener(
            topic = "READ_RECEIPT_TOPIC",
            consumerGroup = "read-receipt-consumer-group"
    )
    @RequiredArgsConstructor
    public static class NettyReadReceiptConsumer implements RocketMQListener<String> {

        private final MessageService messageService;

        @Override
        public void onMessage(String content) {
            log.debug("消费已读回执: content={}", content);

            try {
                // 解析已读消息 ID 列表并更新状态
                // messageService.markMessagesAsRead(messageIds);
            } catch (Exception e) {
                log.error("处理已读回执失败", e);
            }
        }
    }
}
