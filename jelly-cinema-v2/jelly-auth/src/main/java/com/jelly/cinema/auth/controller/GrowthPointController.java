package com.jelly.cinema.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.jelly.cinema.auth.domain.vo.PointLogVO;
import com.jelly.cinema.auth.service.UserPointService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户积分")
@RestController
@RequestMapping("/auth/growth/points")
@RequiredArgsConstructor
@SaCheckLogin
public class GrowthPointController {

    private final UserPointService userPointService;

    @Operation(summary = "获取积分余额")
    @GetMapping("/balance")
    public R<Integer> getBalance() {
        return R.ok(userPointService.getBalance());
    }

    @Operation(summary = "获取积分流水")
    @GetMapping("/logs")
    public R<PageResult<PointLogVO>> getPointLogs(PageQuery query) {
        return R.ok(userPointService.getPointLogs(query));
    }
}
