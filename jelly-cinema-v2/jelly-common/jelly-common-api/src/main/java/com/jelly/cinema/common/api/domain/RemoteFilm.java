package com.jelly.cinema.common.api.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 远程调用电影数据 DTO
 * 用于服务间传输的电影数据结构
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
public class RemoteFilm implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 电影ID
     */
    private Long id;

    /**
     * 电影标题
     */
    private String title;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 播放源地址
     */
    private String videoUrl;

    /**
     * 简介
     */
    private String description;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 评分（0-10）
     */
    private BigDecimal rating;

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
}
