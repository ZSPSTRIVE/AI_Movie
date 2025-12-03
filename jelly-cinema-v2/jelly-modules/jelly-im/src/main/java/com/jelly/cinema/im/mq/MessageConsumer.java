package com.jelly.cinema.im.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.mapper.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 消息消费者 - 异步持久化
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
@RocketMQMessageListener(topic = "IM_MSG_SEND", consumerGroup = "im-consumer-group")
public class MessageConsumer implements RocketMQListener<String> {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public void onMessage(String message) {
        try {
            ChatMessage chatMessage = JSONUtil.toBean(message, ChatMessage.class);
            chatMessageMapper.insert(chatMessage);
            log.debug("消息持久化成功: {}", chatMessage.getId());
        } catch (Exception e) {
            log.error("消息持久化失败: {}", message, e);
        }
    }
}
