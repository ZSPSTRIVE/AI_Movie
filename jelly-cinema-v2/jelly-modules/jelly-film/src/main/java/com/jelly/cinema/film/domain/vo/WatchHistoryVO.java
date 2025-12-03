package com.jelly.cinema.film.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 观看历史 VO
 *
 * @author Jelly Cinema
 */
@Data
public class WatchHistoryVO {

    /**
     * 记录 ID
     */
    private Long id;

    /**
     * 电影 ID
     */
    private Long filmId;

    /**
     * 电影名称
     */
    private String title;

    /**
     * 封面图
     */
    private String poster;

    /**
     * 观看进度（百分比 0-100）
     */
    private Integer progress;

    /**
     * 最后观看时间
     */
    private LocalDateTime watchTime;
}
