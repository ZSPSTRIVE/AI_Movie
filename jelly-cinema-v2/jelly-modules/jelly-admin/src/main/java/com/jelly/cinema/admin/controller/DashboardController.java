package com.jelly.cinema.admin.controller;

import com.jelly.cinema.admin.domain.vo.DashboardStatsVO;
import com.jelly.cinema.admin.domain.vo.DashboardVO;
import com.jelly.cinema.admin.service.DashboardService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "获取统计数据")
    @GetMapping
    public R<DashboardVO> getStatistics() {
        return R.ok(dashboardService.getStatistics());
    }

    @Operation(summary = "获取完整统计数据 (V1.5)")
    @GetMapping("/stats")
    public R<DashboardStatsVO> getFullStats() {
        return R.ok(dashboardService.getFullStats());
    }
}
