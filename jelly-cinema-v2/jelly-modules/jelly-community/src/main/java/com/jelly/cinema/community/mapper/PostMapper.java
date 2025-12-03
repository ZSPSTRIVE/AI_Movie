package com.jelly.cinema.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.community.domain.entity.Post;
import org.apache.ibatis.annotations.Mapper;

/**
 * 帖子 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface PostMapper extends BaseMapper<Post> {

}
