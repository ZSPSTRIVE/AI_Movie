package com.jelly.cinema.auth.mq;

import cn.hutool.json.JSONUtil;
import com.jelly.cinema.auth.domain.event.CouponExchangeEvent;
import com.jelly.cinema.auth.service.impl.CouponServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
@RocketMQMessageListener(
        topic = "GROWTH_COUPON_EXCHANGE",
        consumerGroup = "growth-coupon-group"
)
public class CouponExchangeConsumer implements RocketMQListener<String> {

    private final CouponServiceImpl couponService;

    @Override
    public void onMessage(String message) {
        try {
            CouponExchangeEvent event = JSONUtil.toBean(message, CouponExchangeEvent.class);
            log.info("收到优惠券兑换消息: userId={}, templateId={}", event.getUserId(), event.getTemplateId());

            couponService.saveUserCoupon(event.getUserId(), event.getTemplateId(), event.getExpireTime());

            log.info("优惠券异步落库成功: userId={}, templateId={}", event.getUserId(), event.getTemplateId());
        } catch (Exception e) {
            log.error("优惠券异步落库失败: message={}", message, e);
        }
    }
}
