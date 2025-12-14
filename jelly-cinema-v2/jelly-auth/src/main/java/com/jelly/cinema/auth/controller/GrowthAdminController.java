package com.jelly.cinema.auth.controller;

import com.jelly.cinema.auth.service.CouponService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "优惠券管理(管理员)")
@RestController
@RequestMapping("/auth/growth/admin/coupons")
@RequiredArgsConstructor
public class GrowthAdminController {

    private final CouponService couponService;

    @Operation(summary = "加载优惠券库存到Redis")
    @PostMapping("/templates/{id}/publish")
    public R<Void> publishTemplate(@PathVariable Long id) {
        couponService.loadStockToRedis(id);
        return R.ok();
    }
}
