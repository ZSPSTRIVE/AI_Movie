package com.jelly.cinema.community.service;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.community.domain.dto.CommentCreateDTO;
import com.jelly.cinema.community.domain.vo.CommentVO;

import java.util.List;

/**
 * 评论服务接口
 *
 * @author Jelly Cinema
 */
public interface CommentService {

    /**
     * 分页获取帖子评论列表
     *
     * @param postId 帖子 ID
     * @param query  分页参数
     * @return 评论列表（树形结构）
     */
    PageResult<CommentVO> listByPostId(Long postId, PageQuery query);

    /**
     * 发布评论
     *
     * @param dto 创建参数
     * @return 评论 ID
     */
    Long create(CommentCreateDTO dto);

    /**
     * 删除评论
     *
     * @param id 评论 ID
     */
    void delete(Long id);

    /**
     * 点赞评论
     *
     * @param id 评论 ID
     */
    void like(Long id);

    /**
     * 取消点赞
     *
     * @param id 评论 ID
     */
    void unlike(Long id);
}
