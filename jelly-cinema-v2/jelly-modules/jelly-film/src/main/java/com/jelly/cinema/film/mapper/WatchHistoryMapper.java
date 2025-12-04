package com.jelly.cinema.film.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.film.domain.entity.WatchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 观看历史 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface WatchHistoryMapper extends BaseMapper<WatchHistory> {

    /**
     * 根据用户 ID 查询观看历史
     */
    @Select("SELECT * FROM t_watch_history WHERE user_id = #{userId} ORDER BY watch_time DESC")
    List<WatchHistory> selectByUserId(Long userId);
}
