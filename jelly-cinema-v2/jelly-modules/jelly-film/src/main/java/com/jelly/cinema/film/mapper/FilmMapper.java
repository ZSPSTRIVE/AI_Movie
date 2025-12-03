package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.Film;
import org.apache.ibatis.annotations.Mapper;

/**
 * 电影 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface FilmMapper extends BaseMapper<Film> {

}
