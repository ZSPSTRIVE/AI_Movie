package com.jelly.cinema.im.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 消息生产者
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class MessageProducer {

    private final RocketMQTemplate rocketMQTemplate;

    private static final String TOPIC = "IM_MSG_SEND";

    /**
     * 发送消息到 MQ
     */
    public void sendMessage(ChatMessage message) {
        try {
            String json = JSONUtil.toJsonStr(message);
            rocketMQTemplate.convertAndSend(TOPIC, json);
            log.debug("消息投递 MQ 成功: {}", message.getId());
        } catch (Exception e) {
            log.error("消息投递 MQ 失败", e);
        }
    }
}
