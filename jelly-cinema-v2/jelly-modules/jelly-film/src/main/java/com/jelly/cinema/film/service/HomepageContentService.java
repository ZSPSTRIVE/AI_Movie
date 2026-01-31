package com.jelly.cinema.film.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.entity.HomepageContent;
import com.jelly.cinema.film.domain.vo.HomepageContentVO;

import java.util.List;

/**
 * 首页内容管理服务接口
 *
 * @author Jelly Cinema
 * @since 2026
 */
public interface HomepageContentService extends IService<HomepageContent> {

    /**
     * 分页查询首页内容列表
     */
    PageResult<HomepageContentVO> pageList(String contentType, String sectionType, Integer pageNum, Integer pageSize);

    /**
     * 获取推荐列表（前端首页使用）
     */
    List<HomepageContentVO> getRecommendList(Integer limit);

    /**
     * 获取热门列表
     */
    List<HomepageContentVO> getHotList(Integer limit);

    /**
     * 获取电影列表
     */
    List<HomepageContentVO> getMovieList(Integer limit);

    /**
     * 获取电视剧列表
     */
    List<HomepageContentVO> getTvSeriesList(Integer limit);

    /**
     * 刷新内容（从TVBox采集）
     */
    void refreshContent();

    /**
     * AI智能排序
     */
    void aiSort();

    /**
     * 更新排序
     */
    void updateSortOrder(Long id, Integer sortOrder);

    /**
     * 切换状态
     */
    void toggleStatus(Long id);

    /**
     * 获取AI精选列表
     */
    List<HomepageContentVO> getAiBestList(Integer limit);

    /**
     * 获取新片列表
     */
    List<HomepageContentVO> getNewList(Integer limit);

    /**
     * 获取趋势/热门话题列表
     */
    List<HomepageContentVO> getTrendingList(Integer limit);

    /**
     * 获取分板块的首页内容
     * @return Map: sectionType -> 内容列表
     */
    java.util.Map<String, List<HomepageContentVO>> getSectionedContent();

    /**
     * 标记为AI精选
     */
    void markAsBest(Long id, boolean isBest);
}
