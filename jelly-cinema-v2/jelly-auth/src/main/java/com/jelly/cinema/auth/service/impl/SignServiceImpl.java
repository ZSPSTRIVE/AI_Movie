package com.jelly.cinema.auth.service.impl;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.auth.domain.event.CheckinArchiveEvent;
import com.jelly.cinema.auth.domain.vo.SignStatusVO;
import com.jelly.cinema.auth.service.SignService;
import com.jelly.cinema.auth.service.UserPointService;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SignServiceImpl implements SignService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserPointService userPointService;
    private RocketMQTemplate rocketMQTemplate;

    public SignServiceImpl(RedisTemplate<String, Object> redisTemplate, UserPointService userPointService) {
        this.redisTemplate = redisTemplate;
        this.userPointService = userPointService;
    }

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    private static final String SIGN_KEY_PREFIX = "jelly:growth:sign:";
    private static final int SIGN_POINTS = 10;
    private static final String CHECKIN_ARCHIVE_TOPIC = "GROWTH_CHECKIN_ARCHIVE";

    @Override
    public boolean checkin() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LocalDate today = LocalDate.now();
        String key = buildSignKey(userId, today);
        int dayOfMonth = today.getDayOfMonth();

        Boolean wasSet = redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        if (Boolean.TRUE.equals(wasSet)) {
            throw new ServiceException(4001, "今天已经签到过了");
        }

        userPointService.addPoints(userId, SIGN_POINTS, 1, "每日签到奖励");

        if (rocketMQTemplate != null) {
            try {
                CheckinArchiveEvent event = CheckinArchiveEvent.builder()
                        .userId(userId)
                        .checkinDate(today)
                        .eventTime(LocalDateTime.now())
                        .build();
                rocketMQTemplate.convertAndSend(CHECKIN_ARCHIVE_TOPIC, JSONUtil.toJsonStr(event));
            } catch (Exception e) {
                log.warn("签到归档消息发送失败, userId={}, date={}", userId, today, e);
            }
        }

        log.info("用户签到成功: userId={}, date={}", userId, today);
        return true;
    }

    @Override
    public SignStatusVO getSignStatus() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LocalDate today = LocalDate.now();
        String key = buildSignKey(userId, today);
        int dayOfMonth = today.getDayOfMonth();

        Boolean signedToday = redisTemplate.opsForValue().getBit(key, dayOfMonth - 1);

        List<Long> bitField = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));

        int continuousDays = 0;
        List<Integer> signedDays = new ArrayList<>();

        if (bitField != null && !bitField.isEmpty()) {
            long bits = bitField.get(0);
            // 从今天开始往前计算连续天数
            boolean counting = true;
            for (int i = dayOfMonth; i >= 1; i--) {
                boolean signed = (bits >> (dayOfMonth - i) & 1) == 1;
                if (signed) {
                    signedDays.add(i);
                    if (counting) {
                        continuousDays++;
                    }
                } else {
                    counting = false;
                }
            }
        }

        return SignStatusVO.builder()
                .signedToday(Boolean.TRUE.equals(signedToday))
                .continuousDays(continuousDays)
                .monthTotalDays(signedDays.size())
                .signedDays(signedDays)
                .build();
    }

    private String buildSignKey(Long userId, LocalDate date) {
        String yearMonth = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return SIGN_KEY_PREFIX + userId + ":" + yearMonth;
    }
}
