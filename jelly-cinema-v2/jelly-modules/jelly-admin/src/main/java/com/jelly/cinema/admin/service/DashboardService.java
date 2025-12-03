package com.jelly.cinema.admin.service;

import com.jelly.cinema.admin.domain.vo.DashboardStatsVO;
import com.jelly.cinema.admin.domain.vo.DashboardVO;

/**
 * 仪表盘服务接口
 *
 * @author Jelly Cinema
 */
public interface DashboardService {

    /**
     * 获取统计数据 (基础版)
     */
    DashboardVO getStatistics();

    /**
     * 获取完整统计数据 (V1.5 增强版)
     */
    DashboardStatsVO getFullStats();
}
