package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.TvboxSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * TVBox采集源 Mapper
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Mapper
public interface TvboxSourceMapper extends BaseMapper<TvboxSource> {

    /**
     * 获取启用的采集源列表（按优先级排序）
     */
    @Select("SELECT * FROM t_tvbox_source WHERE enabled = 1 AND deleted = 0 ORDER BY priority ASC")
    List<TvboxSource> selectEnabledSources();
}
