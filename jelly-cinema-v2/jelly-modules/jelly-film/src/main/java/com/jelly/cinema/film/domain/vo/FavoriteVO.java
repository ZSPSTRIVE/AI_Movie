package com.jelly.cinema.film.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏电影 VO
 *
 * @author Jelly Cinema
 */
@Data
public class FavoriteVO {

    /**
     * 收藏记录 ID
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
     * 上映年份
     */
    private Integer year;

    /**
     * 评分
     */
    private Double rating;

    /**
     * 收藏时间
     */
    private LocalDateTime createTime;
}
