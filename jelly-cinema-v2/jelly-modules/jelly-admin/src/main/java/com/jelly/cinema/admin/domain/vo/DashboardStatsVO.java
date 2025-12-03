package com.jelly.cinema.admin.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 仪表盘统计数据 VO
 *
 * @author Jelly Cinema
 */
@Data
public class DashboardStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实时在线人数
     */
    private Integer onlineCount;

    /**
     * 今日消息量
     */
    private Long todayMessageCount;

    /**
     * 今日新增用户
     */
    private Integer todayNewUsers;

    /**
     * 总用户数
     */
    private Long totalUsers;

    /**
     * 活跃群数
     */
    private Integer activeGroups;

    /**
     * 总群数
     */
    private Long totalGroups;

    /**
     * 待处理举报数
     */
    private Integer pendingReports;

    /**
     * 近7日新增用户趋势
     */
    private List<TrendItem> userTrend;

    /**
     * 消息时段分布
     */
    private List<TrendItem> messageDist;

    @Data
    public static class TrendItem {
        private String date;
        private Long value;

        public TrendItem() {}

        public TrendItem(String date, Long value) {
            this.date = date;
            this.value = value;
        }
    }
}
