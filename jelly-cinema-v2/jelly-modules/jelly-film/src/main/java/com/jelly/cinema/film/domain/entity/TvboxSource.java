package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * TVBox采集源配置实体
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Data
@TableName("t_tvbox_source")
public class TvboxSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 源名称
     */
    private String sourceName;

    /**
     * API地址
     */
    private String apiUrl;

    /**
     * API类型：json/xml
     */
    private String apiType;

    /**
     * 是否启用：0-禁用，1-启用
     */
    private Integer enabled;

    /**
     * 优先级（越小越优先）
     */
    private Integer priority;

    /**
     * 采集间隔（分钟）
     */
    private Integer fetchInterval;

    /**
     * 上次采集时间
     */
    private LocalDateTime lastFetchTime;

    /**
     * 采集状态：0-正常，1-失败，2-采集中
     */
    private Integer fetchStatus;

    /**
     * 当前采集电影数量
     */
    private Integer filmCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 备注
     */
    private String remark;

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
