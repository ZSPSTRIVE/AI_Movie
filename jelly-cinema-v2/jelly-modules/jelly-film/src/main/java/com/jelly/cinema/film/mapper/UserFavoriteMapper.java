package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户收藏 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /**
     * 根据用户 ID 查询收藏列表
     */
    @Select("SELECT * FROM t_user_favorite WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<UserFavorite> selectByUserId(Long userId);
}
