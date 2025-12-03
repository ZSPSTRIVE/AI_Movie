package com.jelly.cinema.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.admin.domain.entity.Report;
import com.jelly.cinema.admin.domain.vo.DashboardStatsVO;
import com.jelly.cinema.admin.domain.vo.DashboardVO;
import com.jelly.cinema.admin.mapper.AdminFilmMapper;
import com.jelly.cinema.admin.mapper.AdminPostMapper;
import com.jelly.cinema.admin.mapper.AdminUserMapper;
import com.jelly.cinema.admin.mapper.ReportMapper;
import com.jelly.cinema.admin.service.DashboardService;
import com.jelly.cinema.common.api.feign.RemoteImService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AdminUserMapper userMapper;
    private final AdminFilmMapper filmMapper;
    private final AdminPostMapper postMapper;
    private final ReportMapper reportMapper;
    private final RedisService redisService;
    private final RemoteImService remoteImService;

    private static final String ONLINE_COUNT_KEY = "im:online:count";
    private static final String MSG_COUNT_KEY = "im:msg:count:";
    private static final String MSG_HOUR_KEY = "im:msg:hour:";

    @Override
    public DashboardVO getStatistics() {
        DashboardVO vo = new DashboardVO();
        vo.setUserCount(userMapper.countTotal());
        vo.setFilmCount(filmMapper.countTotal());
        vo.setPostCount(postMapper.countTotal());
        vo.setTodayNewUsers(userMapper.countTodayNew());
        vo.setTodayPlayCount(filmMapper.sumPlayCount());
        vo.setWeeklyActiveUsers(0L);
        return vo;
    }

    @Override
    public DashboardStatsVO getFullStats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        
        // 实时在线数 (从 Redis 获取)
        Integer onlineCount = redisService.get(ONLINE_COUNT_KEY);
        vo.setOnlineCount(onlineCount != null ? onlineCount : 0);
        
        // 今日消息量 (从 Redis 获取)
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Long msgCount = redisService.get(MSG_COUNT_KEY + today);
        vo.setTodayMessageCount(msgCount != null ? msgCount : 0L);
        
        // 用户统计
        Long todayNew = userMapper.countTodayNew();
        vo.setTodayNewUsers(todayNew != null ? Math.toIntExact(todayNew) : 0);
        vo.setTotalUsers(userMapper.countTotal());
        
        // 群组统计（调用 IM 服务）
        try {
            R<Long> groupCountResult = remoteImService.getGroupCount();
            if (groupCountResult.isSuccess() && groupCountResult.getData() != null) {
                vo.setTotalGroups(groupCountResult.getData());
            } else {
                vo.setTotalGroups(0L);
            }
            
            R<Integer> activeGroupResult = remoteImService.getActiveGroupCount();
            if (activeGroupResult.isSuccess() && activeGroupResult.getData() != null) {
                vo.setActiveGroups(activeGroupResult.getData());
            } else {
                vo.setActiveGroups(0);
            }
        } catch (Exception e) {
            log.warn("获取群组统计失败: {}", e.getMessage());
            vo.setTotalGroups(0L);
            vo.setActiveGroups(0);
        }
        
        // 待处理举报数
        LambdaQueryWrapper<Report> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(Report::getStatus, Report.STATUS_PENDING);
        vo.setPendingReports(Math.toIntExact(reportMapper.selectCount(reportWrapper)));
        
        // 近7日新增用户趋势
        vo.setUserTrend(getLast7DaysUserTrend());
        
        // 消息时段分布 (模拟数据)
        vo.setMessageDist(getMessageDistribution());
        
        return vo;
    }

    private List<DashboardStatsVO.TrendItem> getLast7DaysUserTrend() {
        List<DashboardStatsVO.TrendItem> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MM-dd");
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            // 从数据库查询每日新增用户数
            Long count = userMapper.countByDate(date.format(dbFormatter));
            trend.add(new DashboardStatsVO.TrendItem(date.format(displayFormatter), count != null ? count : 0L));
        }
        
        return trend;
    }

    private List<DashboardStatsVO.TrendItem> getMessageDistribution() {
        List<DashboardStatsVO.TrendItem> dist = new ArrayList<>();
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String[] periods = {"00-04", "04-08", "08-12", "12-16", "16-20", "20-24"};
        int[][] hourRanges = {{0, 4}, {4, 8}, {8, 12}, {12, 16}, {16, 20}, {20, 24}};
        
        for (int i = 0; i < periods.length; i++) {
            long totalCount = 0;
            // 从 Redis 累加该时段内每小时的消息数
            for (int h = hourRanges[i][0]; h < hourRanges[i][1]; h++) {
                String key = MSG_HOUR_KEY + today + ":" + String.format("%02d", h);
                Long count = redisService.get(key);
                if (count != null) {
                    totalCount += count;
                }
            }
            dist.add(new DashboardStatsVO.TrendItem(periods[i], totalCount));
        }
        
        return dist;
    }
}
