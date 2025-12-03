package com.jelly.cinema.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 电影创建/编辑 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class FilmCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 电影ID（编辑时传入）
     */
    private Long id;

    /**
     * 电影名
     */
    @NotBlank(message = "电影名不能为空")
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
     * 标签
     */
    private List<String> tags;

    /**
     * 评分
     */
    private BigDecimal rating;

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
