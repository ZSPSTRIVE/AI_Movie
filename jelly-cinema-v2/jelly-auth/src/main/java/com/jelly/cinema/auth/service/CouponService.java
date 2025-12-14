package com.jelly.cinema.auth.service;

import com.jelly.cinema.auth.domain.vo.CouponTemplateVO;
import com.jelly.cinema.auth.domain.vo.UserCouponVO;

import java.util.List;

public interface CouponService {

    List<CouponTemplateVO> listTemplates();

    void exchange(Long templateId);

    List<UserCouponVO> myCoupons();

    void useCoupon(Long couponId);

    void loadStockToRedis(Long templateId);
}
