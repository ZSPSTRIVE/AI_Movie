package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页配置版本实体
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
@TableName("t_homepage_config_version")
public class HomepageConfigVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本号 如 v1.0.0
     */
    private String version;

    /**
     * 状态：draft/published/archived
     */
    private String status;

    /**
     * 分类：movie/tv_series/variety/anime
     */
    private String category;

    /**
     * 完整配置JSON快照
     */
    private String configJson;

    /**
     * 配置内容哈希
     */
    private String checksum;

    /**
     * 发布说明
     */
    private String publishNote;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 回滚来源版本
     */
    private String rollbackFrom;

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
