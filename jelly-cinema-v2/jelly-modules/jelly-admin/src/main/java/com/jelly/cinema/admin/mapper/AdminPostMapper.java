package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 帖子 Mapper（管理端）
 *
 * @author Jelly Cinema
 */
@Mapper
public interface AdminPostMapper {

    /**
     * 统计帖子总数
     */
    @Select("SELECT COUNT(*) FROM t_post WHERE deleted = 0 AND status = 0")
    Long countTotal();
}
