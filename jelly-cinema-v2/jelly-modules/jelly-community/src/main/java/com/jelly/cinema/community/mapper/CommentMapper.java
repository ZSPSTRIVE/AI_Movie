package com.jelly.cinema.community.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.community.domain.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

}
