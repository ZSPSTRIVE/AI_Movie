package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 观看历史实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_watch_history")
public class WatchHistory extends BaseEntity {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 电影 ID
     */
    private Long filmId;

    /**
     * 观看进度（百分比 0-100）
     */
    private Integer progress;

    /**
     * 最后观看时间
     */
    private LocalDateTime watchTime;
}
