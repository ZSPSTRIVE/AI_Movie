package com.jelly.cinema.community.service;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.community.domain.dto.PostCreateDTO;
import com.jelly.cinema.community.domain.vo.PostVO;

/**
 * 帖子服务接口
 *
 * @author Jelly Cinema
 */
public interface PostService {

    /**
     * 分页查询帖子列表
     *
     * @param query   分页参数
     * @param keyword 搜索关键词
     * @param filmId  关联电影 ID
     * @return 分页结果
     */
    PageResult<PostVO> list(PageQuery query, String keyword, Long filmId);

    /**
     * 获取帖子详情
     *
     * @param id 帖子 ID
     * @return 帖子详情
     */
    PostVO getDetail(Long id);

    /**
     * 发布帖子
     *
     * @param dto 创建参数
     * @return 帖子 ID
     */
    Long create(PostCreateDTO dto);

    /**
     * 删除帖子
     *
     * @param id 帖子 ID
     */
    void delete(Long id);

    /**
     * 投票（赞同/反对）
     *
     * @param id   帖子 ID
     * @param type 1-赞同，-1-反对，0-取消
     */
    void vote(Long id, Integer type);

    /**
     * 增加浏览量
     *
     * @param id 帖子 ID
     */
    void incrementViewCount(Long id);

    /**
     * 获取当前用户的帖子列表
     *
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<PostVO> listMyPosts(PageQuery query);
}
