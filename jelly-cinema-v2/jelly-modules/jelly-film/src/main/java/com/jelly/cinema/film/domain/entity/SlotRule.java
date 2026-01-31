package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 坑位规则实体
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
@TableName("t_slot_rule")
public class SlotRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类
     */
    private String category;

    /**
     * 板块类型
     */
    private String sectionType;

    /**
     * 坑位唯一标识
     */
    private String slotId;

    /**
     * 排序位置
     */
    private Integer position;

    /**
     * 是否锁定不可替换（1=锁定）
     */
    private Integer locked;

    /**
     * 是否可自动替换（1=是）
     */
    private Integer replaceable;

    /**
     * 最低评分要求
     */
    private BigDecimal minRating;

    /**
     * 偏好类型JSON数组
     */
    private String preferredGenres;

    /**
     * 偏好地区
     */
    private String preferredRegions;

    /**
     * 年份范围
     */
    private String yearRange;

    /**
     * 重复曝光间隔（天）
     */
    private Integer exposureIntervalDays;

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
}
