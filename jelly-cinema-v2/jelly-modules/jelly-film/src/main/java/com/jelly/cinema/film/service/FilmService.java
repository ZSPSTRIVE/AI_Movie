package com.jelly.cinema.film.service;

import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.dto.FilmQueryDTO;
import com.jelly.cinema.film.domain.vo.FilmVO;

import java.util.List;
import java.util.Map;

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

    /**
     * 保存从 TVBox 获取的电影数据
     *
     * @param data 电影数据 Map
     * @return 是否保存成功 (true-新增, false-已存在或忽略)
     */
    boolean saveFromTvbox(java.util.Map<String, Object> data);

    /**
     * 将电影库中的数据同步到 Python RAG
     *
     * @param limit 同步数量，null 时使用默认值
     * @return 实际同步成功数量
     */
    int syncFilmsToRag(Integer limit);

    /**
     * 后台触发电影向量库同步任务
     *
     * @param limit 同步数量，null 时使用默认值
     * @return 当前任务状态
     */
    Map<String, Object> startSyncFilmsToRag(Integer limit);

    /**
     * 获取电影向量库同步任务状态
     *
     * @return 当前任务状态
     */
    Map<String, Object> getSyncFilmsToRagStatus();

    /**
     * 启动阶段从 TVBox 预热电影库
     *
     * @param targetNewCount 目标新增数量
     * @param fullSweep      是否执行完整补库（推荐 + 列表 + 关键词）
     * @return 实际新增数量
     */
    int warmupCatalogFromTvbox(int targetNewCount, boolean fullSweep);
}
