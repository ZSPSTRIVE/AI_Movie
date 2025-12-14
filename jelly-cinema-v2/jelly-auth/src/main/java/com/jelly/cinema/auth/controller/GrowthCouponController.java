package com.jelly.cinema.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.jelly.cinema.auth.domain.vo.CouponTemplateVO;
import com.jelly.cinema.auth.domain.vo.UserCouponVO;
import com.jelly.cinema.auth.service.CouponService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "优惠券兑换")
@RestController
@RequestMapping("/auth/growth/coupons")
@RequiredArgsConstructor
@SaCheckLogin
public class GrowthCouponController {

    private final CouponService couponService;

    @Operation(summary = "获取可兑换优惠券列表")
    @GetMapping("/templates")
    public R<List<CouponTemplateVO>> listTemplates() {
        return R.ok(couponService.listTemplates());
    }

    @Operation(summary = "兑换优惠券")
    @PostMapping("/{templateId}/exchange")
    public R<Void> exchange(@PathVariable Long templateId) {
        couponService.exchange(templateId);
        return R.ok();
    }

    @Operation(summary = "我的优惠券")
    @GetMapping("/my")
    public R<List<UserCouponVO>> myCoupons() {
        return R.ok(couponService.myCoupons());
    }

    @Operation(summary = "使用优惠券")
    @PostMapping("/{couponId}/use")
    public R<Void> useCoupon(@PathVariable Long couponId) {
        couponService.useCoupon(couponId);
        return R.ok();
    }
}
