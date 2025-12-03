package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收藏 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
