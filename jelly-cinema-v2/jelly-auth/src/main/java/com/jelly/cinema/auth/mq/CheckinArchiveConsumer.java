package com.jelly.cinema.auth.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.auth.domain.entity.UserCheckinLog;
import com.jelly.cinema.auth.domain.event.CheckinArchiveEvent;
import com.jelly.cinema.auth.mapper.UserCheckinLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
@RocketMQMessageListener(
        topic = "GROWTH_CHECKIN_ARCHIVE",
        consumerGroup = "growth-checkin-group"
)
public class CheckinArchiveConsumer implements RocketMQListener<String> {

    private final UserCheckinLogMapper userCheckinLogMapper;

    @Override
    public void onMessage(String message) {
        try {
            CheckinArchiveEvent event = JSONUtil.toBean(message, CheckinArchiveEvent.class);
            log.info("收到签到归档消息: userId={}, date={}", event.getUserId(), event.getCheckinDate());

            UserCheckinLog checkinLog = new UserCheckinLog();
            checkinLog.setUserId(event.getUserId());
            checkinLog.setCheckinDate(event.getCheckinDate());
            checkinLog.setCreateTime(LocalDateTime.now());

            userCheckinLogMapper.insert(checkinLog);

            log.info("签到归档成功: userId={}, date={}", event.getUserId(), event.getCheckinDate());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                log.info("签到记录已存在，跳过: message={}", message);
            } else {
                log.error("签到归档失败: message={}", message, e);
            }
        }
    }
}
