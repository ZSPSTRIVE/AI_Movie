package com.jelly.cinema.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.community.domain.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 帖子 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE t_post SET vote_up = vote_up + #{delta} WHERE id = #{id} AND vote_up + #{delta} >= 0")
    int updateVoteUp(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE t_post SET vote_down = vote_down + #{delta} WHERE id = #{id} AND vote_down + #{delta} >= 0")
    int updateVoteDown(@Param("id") Long id, @Param("delta") int delta);
}
