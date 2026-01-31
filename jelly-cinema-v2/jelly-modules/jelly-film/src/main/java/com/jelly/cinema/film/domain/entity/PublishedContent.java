package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已发布资源快照实体
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
@TableName("t_published_content")
public class PublishedContent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属配置版本
     */
    private String configVersion;

    /**
     * 分类
     */
    private String category;

    /**
     * 板块类型
     */
    private String sectionType;

    /**
     * 对应坑位ID
     */
    private String slotId;

    /**
     * 展示顺序
     */
    private Integer position;

    /**
     * 归一化资源ID
     */
    private String canonicalId;

    /**
     * 原始TVBox ID
     */
    private String tvboxId;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面图
     */
    private String coverUrl;

    /**
     * 播放地址
     */
    private String playUrl;

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
     * 来源名称
     */
    private String sourceName;

    /**
     * 是否是锁定坑位
     */
    private Integer locked;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
