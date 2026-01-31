package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 首页内容实体
 * 用于管理首页展示的电影/电视剧内容
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
@TableName("t_homepage_content")
public class HomepageContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 内容类型：movie/tv_series/variety/anime
     */
    private String contentType;

    /**
     * 板块类型：recommend/hot/new/trending
     */
    private String sectionType;

    /**
     * TVBox电影ID（Base64编码）
     */
    private String tvboxId;

    /**
     * 电影/剧集标题
     */
    private String title;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 简介
     */
    private String description;

    /**
     * 来源名称（量子/非凡等）
     */
    private String sourceName;

    /**
     * 来源API地址
     */
    private String sourceApi;

    /**
     * 评分
     */
    private BigDecimal rating;

    /**
     * 年份
     */
    private Integer year;

    /**
     * 地区
     */
    private String region;

    /**
     * 主演
     */
    private String actors;

    /**
     * 导演
     */
    private String director;

    /**
     * 排序顺序（越小越靠前）
     */
    private Integer sortOrder;

    /**
     * AI推荐分数
     */
    private BigDecimal aiScore;

    /**
     * AI推荐理由
     */
    private String aiReason;

    /**
     * 是否AI精选: 0-否, 1-是
     */
    private Integer aiBest;

    /**
     * 话题热度值
     */
    private BigDecimal trendingScore;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;
}
