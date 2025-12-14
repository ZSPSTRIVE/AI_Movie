package com.jelly.cinema.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jelly.cinema.auth.domain.entity.CouponTemplate;
import com.jelly.cinema.auth.domain.entity.UserCoupon;
import com.jelly.cinema.auth.domain.event.CouponExchangeEvent;
import com.jelly.cinema.auth.domain.vo.CouponTemplateVO;
import com.jelly.cinema.auth.domain.vo.UserCouponVO;
import com.jelly.cinema.auth.mapper.CouponTemplateMapper;
import com.jelly.cinema.auth.mapper.UserCouponMapper;
import com.jelly.cinema.auth.service.CouponService;
import com.jelly.cinema.auth.service.UserPointService;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CouponServiceImpl implements CouponService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final UserPointService userPointService;
    private final RedisTemplate<String, Object> redisTemplate;
    private RocketMQTemplate rocketMQTemplate;

    public CouponServiceImpl(CouponTemplateMapper couponTemplateMapper,
                             UserCouponMapper userCouponMapper,
                             UserPointService userPointService,
                             RedisTemplate<String, Object> redisTemplate) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.userCouponMapper = userCouponMapper;
        this.userPointService = userPointService;
        this.redisTemplate = redisTemplate;
    }

    @Autowired(required = false)
    public void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    private static final String STOCK_KEY_PREFIX = "jelly:growth:coupon:stock:";
    private static final String USERS_KEY_PREFIX = "jelly:growth:coupon:users:";
    private static final String COUPON_EXCHANGE_TOPIC = "GROWTH_COUPON_EXCHANGE";

    private DefaultRedisScript<Long> exchangeScript;
    private DefaultRedisScript<Long> revertScript;

    @PostConstruct
    public void init() {
        exchangeScript = new DefaultRedisScript<>();
        exchangeScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/exchange_coupon.lua")));
        exchangeScript.setResultType(Long.class);

        revertScript = new DefaultRedisScript<>();
        revertScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/revert_coupon.lua")));
        revertScript.setResultType(Long.class);
    }

    @Override
    public List<CouponTemplateVO> listTemplates() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<CouponTemplate> wrapper = new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getStatus, 1)
                .le(CouponTemplate::getStartTime, now)
                .ge(CouponTemplate::getEndTime, now)
                .orderByAsc(CouponTemplate::getPointsRequired);

        List<CouponTemplate> templates = couponTemplateMapper.selectList(wrapper);

        return templates.stream().map(t -> {
            CouponTemplateVO vo = BeanUtil.copyProperties(t, CouponTemplateVO.class);
            String stockKey = STOCK_KEY_PREFIX + t.getId();
            Object stock = redisTemplate.opsForValue().get(stockKey);
            vo.setRemainStock(stock != null ? Integer.parseInt(stock.toString()) : 0);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void exchange(Long templateId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null || template.getStatus() != 1) {
            throw new ServiceException(4104, "优惠券不存在或已下架");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(template.getStartTime())) {
            throw new ServiceException(4104, "活动尚未开始");
        }
        if (now.isAfter(template.getEndTime())) {
            throw new ServiceException(4104, "活动已结束");
        }

        String stockKey = STOCK_KEY_PREFIX + templateId;
        String usersKey = USERS_KEY_PREFIX + templateId;

        Long result = redisTemplate.execute(exchangeScript,
                Arrays.asList(stockKey, usersKey),
                userId.toString());

        if (result == null || result == -1) {
            throw new ServiceException(4101, "您已兑换过该优惠券");
        }
        if (result == -2) {
            throw new ServiceException(4102, "库存不足");
        }

        boolean deducted = userPointService.deductPoints(userId, template.getPointsRequired(), 3,
                "兑换优惠券: " + template.getTitle());

        if (!deducted) {
            redisTemplate.execute(revertScript,
                    Arrays.asList(stockKey, usersKey),
                    userId.toString());
            throw new ServiceException(4103, "积分不足");
        }

        LocalDateTime expireTime = template.getEndTime().plusDays(7);

        if (rocketMQTemplate != null) {
            try {
                CouponExchangeEvent event = CouponExchangeEvent.builder()
                        .userId(userId)
                        .templateId(templateId)
                        .expireTime(expireTime)
                        .eventTime(LocalDateTime.now())
                        .build();
                rocketMQTemplate.convertAndSend(COUPON_EXCHANGE_TOPIC, JSONUtil.toJsonStr(event));
            } catch (Exception e) {
                log.warn("MQ发送失败，降级同步落库: userId={}, templateId={}", userId, templateId, e);
                saveUserCoupon(userId, templateId, expireTime);
            }
        } else {
            saveUserCoupon(userId, templateId, expireTime);
        }

        log.info("优惠券兑换成功: userId={}, templateId={}", userId, templateId);
    }

    @Override
    public List<UserCouponVO> myCoupons() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getCreateTime);

        List<UserCoupon> coupons = userCouponMapper.selectList(wrapper);

        List<Long> templateIds = coupons.stream()
                .map(UserCoupon::getTemplateId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> titleMap = templateIds.isEmpty() ? Map.of() :
                couponTemplateMapper.selectBatchIds(templateIds).stream()
                        .collect(Collectors.toMap(CouponTemplate::getId, CouponTemplate::getTitle));

        return coupons.stream().map(c -> {
            UserCouponVO vo = BeanUtil.copyProperties(c, UserCouponVO.class);
            vo.setTitle(titleMap.getOrDefault(c.getTemplateId(), "未知优惠券"));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void useCoupon(Long couponId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        UserCoupon coupon = userCouponMapper.selectById(couponId);
        if (coupon == null || !coupon.getUserId().equals(userId)) {
            throw new ServiceException("优惠券不存在");
        }
        if (coupon.getStatus() == 1) {
            throw new ServiceException("优惠券已使用");
        }
        if (coupon.getStatus() == 2 || LocalDateTime.now().isAfter(coupon.getExpireTime())) {
            throw new ServiceException("优惠券已过期");
        }

        LambdaUpdateWrapper<UserCoupon> updateWrapper = new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, couponId)
                .eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getUseTime, LocalDateTime.now());

        userCouponMapper.update(null, updateWrapper);
        log.info("优惠券使用成功: userId={}, couponId={}", userId, couponId);
    }

    @Override
    public void loadStockToRedis(Long templateId) {
        CouponTemplate template = couponTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new ServiceException("模板不存在");
        }

        int remainStock = template.getTotalCount() - template.getUsedCount();
        String stockKey = STOCK_KEY_PREFIX + templateId;
        redisTemplate.opsForValue().set(stockKey, remainStock);

        log.info("优惠券库存已加载到Redis: templateId={}, stock={}", templateId, remainStock);
    }

    public void saveUserCoupon(Long userId, Long templateId, LocalDateTime expireTime) {
        LambdaQueryWrapper<UserCoupon> existWrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getTemplateId, templateId);

        if (userCouponMapper.selectCount(existWrapper) > 0) {
            log.info("用户优惠券已存在，跳过: userId={}, templateId={}", userId, templateId);
            return;
        }

        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setTemplateId(templateId);
        userCoupon.setStatus(0);
        userCoupon.setCreateTime(LocalDateTime.now());
        userCoupon.setExpireTime(expireTime);
        userCouponMapper.insert(userCoupon);
    }
}
