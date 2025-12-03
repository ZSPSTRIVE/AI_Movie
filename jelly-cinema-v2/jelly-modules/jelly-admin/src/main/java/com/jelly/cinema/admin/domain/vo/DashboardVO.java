package com.jelly.cinema.admin.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 仪表盘统计 VO
 *
 * @author Jelly Cinema
 */
@Data
public class DashboardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户总数
     */
    private Long userCount;

    /**
     * 电影总数
     */
    private Long filmCount;

    /**
     * 帖子总数
     */
    private Long postCount;

    /**
     * 今日新增用户
     */
    private Long todayNewUsers;

    /**
     * 今日播放量
     */
    private Long todayPlayCount;

    /**
     * 本周活跃用户
     */
    private Long weeklyActiveUsers;
}
