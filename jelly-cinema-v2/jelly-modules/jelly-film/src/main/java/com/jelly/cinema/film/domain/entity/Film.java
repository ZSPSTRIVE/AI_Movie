package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 电影实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_film")
public class Film extends BaseEntity {

    /**
     * 电影名
     */
    private String title;

    /**
     * 封面图 URL
     */
    private String coverUrl;

    /**
     * 播放源地址 (M3U8/MP4)
     */
    private String videoUrl;

    /**
     * 简介 (用于 Embedding)
     */
    private String description;

    /**
     * 分类 ID
     */
    private Long categoryId;

    /**
     * 标签 (JSON 数组)
     */
    private String tags;

    /**
     * 评分 (0-10)
     */
    private Double rating;

    /**
     * 播放量
     */
    private Long playCount;

    /**
     * 上映年份
     */
    private Integer year;

    /**
     * 导演
     */
    private String director;

    /**
     * 主演
     */
    private String actors;

    /**
     * 地区
     */
    private String region;

    /**
     * 时长（分钟）
     */
    private Integer duration;

    /**
     * 状态：0-上架，1-下架
     */
    private Integer status;
}
