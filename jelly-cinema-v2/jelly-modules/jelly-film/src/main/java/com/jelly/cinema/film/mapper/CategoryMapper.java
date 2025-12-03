package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

}
