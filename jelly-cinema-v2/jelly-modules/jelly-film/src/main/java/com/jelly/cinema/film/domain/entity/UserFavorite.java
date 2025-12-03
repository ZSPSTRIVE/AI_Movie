package com.jelly.cinema.film.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户收藏实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_favorite")
public class UserFavorite extends BaseEntity {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 电影 ID
     */
    private Long filmId;
}
