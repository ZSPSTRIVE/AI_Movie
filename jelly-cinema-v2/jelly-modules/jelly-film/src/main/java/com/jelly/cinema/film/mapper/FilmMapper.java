package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.Film;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 电影 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface FilmMapper extends BaseMapper<Film> {

    /**
     * 查询所有电影 ID（用于 BloomFilter 预热）
     */
    @Select("SELECT id FROM t_film WHERE status = 1")
    List<Long> selectAllIds();
}
