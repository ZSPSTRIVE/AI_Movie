package com.jelly.cinema.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 电影实体（管理端）
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_film", autoResultMap = true)
public class Film extends BaseEntity {

    /**
     * 电影名
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
     * 标签
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    /**
     * 评分
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

    /**
     * 状态：0-上架，1-下架
     */
    private Integer status;
}
