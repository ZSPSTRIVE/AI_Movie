package com.jelly.cinema.im.netty.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.netty.model.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 消息生产者
 * 
 * 负责：
 * 1. 发送聊天消息到 MQ
 * 2. 管理消息投递状态
 * 3. 处理离线消息
 * 4. 消息重试机制
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component("nettyMessageProducer")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class MessageProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * MQ Topic
     */
    private static final String TOPIC_CHAT_MESSAGE = "CHAT_MESSAGE_TOPIC";
    private static final String TOPIC_GROUP_MESSAGE = "GROUP_MESSAGE_TOPIC";
    private static final String TOPIC_MESSAGE_ROUTE = "MESSAGE_ROUTE_TOPIC";
    private static final String TOPIC_READ_RECEIPT = "READ_RECEIPT_TOPIC";

    /**
     * Redis Key 前缀
     */
    private static final String OFFLINE_MESSAGE_KEY = "im:offline:msg:";
    private static final String MESSAGE_STATUS_KEY = "im:msg:status:";
    private static final String MESSAGE_RETRY_KEY = "im:msg:retry:";

    /**
     * 消息过期时间（7天）
     */
    private static final long MESSAGE_EXPIRE_DAYS = 7;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 发送私聊消息到 MQ
     */
    public void sendChatMessage(ChatMessageDTO chatMessage) {
        try {
            // 1. 记录消息状态（发送中）
            saveMessageStatus(chatMessage.getMessageId(), "SENDING");

            // 2. 发送到 MQ
            rocketMQTemplate.syncSend(TOPIC_CHAT_MESSAGE, 
                    MessageBuilder.withPayload(chatMessage).build());

            log.info("私聊消息发送到 MQ: msgId={}, from={}, to={}", 
                    chatMessage.getMessageId(), chatMessage.getSenderId(), chatMessage.getReceiverId());

        } catch (Exception e) {
            log.error("发送私聊消息到 MQ 失败: msgId={}", chatMessage.getMessageId(), e);
            // 加入重试队列
            addToRetryQueue(chatMessage);
        }
    }

    /**
     * 发送群聊消息到 MQ
     */
    public void sendGroupMessage(ChatMessageDTO chatMessage) {
        try {
            saveMessageStatus(chatMessage.getMessageId(), "SENDING");

            rocketMQTemplate.syncSend(TOPIC_GROUP_MESSAGE, 
                    MessageBuilder.withPayload(chatMessage).build());

            log.info("群聊消息发送到 MQ: msgId={}, from={}, groupId={}", 
                    chatMessage.getMessageId(), chatMessage.getSenderId(), chatMessage.getReceiverId());

        } catch (Exception e) {
            log.error("发送群聊消息到 MQ 失败: msgId={}", chatMessage.getMessageId(), e);
            addToRetryQueue(chatMessage);
        }
    }

    /**
     * 路由消息到其他服务器
     */
    public void routeToOtherServer(ChatMessageDTO chatMessage) {
        try {
            rocketMQTemplate.syncSend(TOPIC_MESSAGE_ROUTE, 
                    MessageBuilder.withPayload(chatMessage).build());

            log.info("消息路由到其他服务器: msgId={}, to={}", 
                    chatMessage.getMessageId(), chatMessage.getReceiverId());

        } catch (Exception e) {
            log.error("消息路由失败: msgId={}", chatMessage.getMessageId(), e);
            // 保存为离线消息
            saveOfflineMessage(chatMessage);
        }
    }

    /**
     * 发送已读回执到 MQ
     */
    public void sendReadReceipt(Long userId, String content) {
        try {
            rocketMQTemplate.asyncSend(TOPIC_READ_RECEIPT, 
                    MessageBuilder.withPayload(content)
                            .setHeader("userId", userId)
                            .build(),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.debug("已读回执发送成功: userId={}", userId);
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("已读回执发送失败: userId={}", userId, e);
                        }
                    });
        } catch (Exception e) {
            log.error("发送已读回执失败: userId={}", userId, e);
        }
    }

    /**
     * 保存离线消息
     */
    public void saveOfflineMessage(ChatMessageDTO chatMessage) {
        try {
            String key = OFFLINE_MESSAGE_KEY + chatMessage.getReceiverId();
            String messageJson = JSONUtil.toJsonStr(chatMessage);

            // 使用 ZSet 存储，score 为时间戳，便于按时间排序
            redisTemplate.opsForZSet().add(key, messageJson, chatMessage.getTimestamp());

            // 设置过期时间
            redisTemplate.expire(key, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);

            log.info("离线消息保存成功: userId={}, msgId={}", 
                    chatMessage.getReceiverId(), chatMessage.getMessageId());

        } catch (Exception e) {
            log.error("保存离线消息失败: msgId={}", chatMessage.getMessageId(), e);
        }
    }

    /**
     * 推送离线消息
     */
    public void pushOfflineMessages(Long userId) {
        try {
            String key = OFFLINE_MESSAGE_KEY + userId;
            
            // 获取所有离线消息
            Set<Object> messages = redisTemplate.opsForZSet().range(key, 0, -1);
            
            if (messages != null && !messages.isEmpty()) {
                log.info("推送离线消息: userId={}, count={}", userId, messages.size());
                
                // 通过 MQ 发送离线消息推送任务
                for (Object messageJson : messages) {
                    ChatMessageDTO chatMessage = JSONUtil.toBean(messageJson.toString(), ChatMessageDTO.class);
                    
                    // 标记为离线消息
                    rocketMQTemplate.sendOneWay(TOPIC_MESSAGE_ROUTE + ":offline", 
                            MessageBuilder.withPayload(chatMessage)
                                    .setHeader("targetUserId", userId)
                                    .build());
                }

                // 清空离线消息
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.error("推送离线消息失败: userId={}", userId, e);
        }
    }

    /**
     * 确认消息已送达
     */
    public void confirmMessageDelivered(Long messageId) {
        try {
            saveMessageStatus(messageId, "DELIVERED");
            
            // 从重试队列移除
            redisTemplate.delete(MESSAGE_RETRY_KEY + messageId);
            
            log.debug("消息确认送达: msgId={}", messageId);
        } catch (Exception e) {
            log.error("确认消息送达失败: msgId={}", messageId, e);
        }
    }

    /**
     * 保存消息状态
     */
    private void saveMessageStatus(Long messageId, String status) {
        String key = MESSAGE_STATUS_KEY + messageId;
        redisTemplate.opsForValue().set(key, status, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    /**
     * 加入重试队列
     */
    private void addToRetryQueue(ChatMessageDTO chatMessage) {
        try {
            String key = MESSAGE_RETRY_KEY + chatMessage.getMessageId();
            String countKey = key + ":count";

            // 检查重试次数
            Integer retryCount = (Integer) redisTemplate.opsForValue().get(countKey);
            if (retryCount == null) {
                retryCount = 0;
            }

            if (retryCount >= MAX_RETRY_COUNT) {
                log.warn("消息重试次数已达上限，放弃重试: msgId={}", chatMessage.getMessageId());
                saveMessageStatus(chatMessage.getMessageId(), "FAILED");
                return;
            }

            // 保存到重试队列
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(chatMessage), 
                    5 * (retryCount + 1), TimeUnit.MINUTES);
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, MESSAGE_EXPIRE_DAYS, TimeUnit.DAYS);

            log.info("消息加入重试队列: msgId={}, retryCount={}", 
                    chatMessage.getMessageId(), retryCount + 1);

        } catch (Exception e) {
            log.error("加入重试队列失败: msgId={}", chatMessage.getMessageId(), e);
        }
    }

    /**
     * 获取消息状态
     */
    public String getMessageStatus(Long messageId) {
        String key = MESSAGE_STATUS_KEY + messageId;
        Object status = redisTemplate.opsForValue().get(key);
        return status != null ? status.toString() : null;
    }
}
