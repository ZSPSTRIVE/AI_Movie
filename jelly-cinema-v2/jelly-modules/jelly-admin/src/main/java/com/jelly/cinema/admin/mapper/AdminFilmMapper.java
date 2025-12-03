package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.Film;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 电影 Mapper（管理端）
 *
 * @author Jelly Cinema
 */
@Mapper
public interface AdminFilmMapper extends BaseMapper<Film> {

    /**
     * 统计电影总数
     */
    @Select("SELECT COUNT(*) FROM t_film WHERE deleted = 0")
    Long countTotal();

    /**
     * 统计今日播放量
     */
    @Select("SELECT COALESCE(SUM(play_count), 0) FROM t_film WHERE deleted = 0")
    Long sumPlayCount();
}
