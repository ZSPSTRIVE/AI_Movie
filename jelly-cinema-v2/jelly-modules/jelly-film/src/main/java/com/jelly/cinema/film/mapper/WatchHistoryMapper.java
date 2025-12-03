package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.WatchHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 观看历史 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {
}
