package com.jelly.cinema.film.service;

import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.dto.FilmQueryDTO;
import com.jelly.cinema.film.domain.vo.FilmVO;

import java.util.List;

/**
 * 电影服务接口
 *
 * @author Jelly Cinema
 */
public interface FilmService {

    /**
     * 分页查询电影列表
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    PageResult<FilmVO> list(FilmQueryDTO dto);

    /**
     * 获取电影详情
     *
     * @param id 电影 ID
     * @return 电影详情
     */
    FilmVO getDetail(Long id);

    /**
     * 搜索电影
     *
     * @param keyword 关键词
     * @return 电影列表
     */
    List<FilmVO> search(String keyword);

    /**
     * 获取推荐电影
     *
     * @param size 数量
     * @return 电影列表
     */
    List<FilmVO> getRecommend(Integer size);

    /**
     * 获取热门榜单
     *
     * @param size 数量
     * @return 电影列表
     */
    List<FilmVO> getHotRank(Integer size);

    /**
     * 增加播放量
     *
     * @param id 电影 ID
     */
    void incrementPlayCount(Long id);

    /**
     * 根据 ID 列表获取电影（保持顺序）
     *
     * @param ids 电影 ID 列表
     * @return 电影列表
     */
    List<FilmVO> getFilmsByIds(List<Long> ids);
}
