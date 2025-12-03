package com.jelly.cinema.film.service;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.vo.FavoriteVO;

/**
 * 用户收藏服务接口
 *
 * @author Jelly Cinema
 */
public interface UserFavoriteService {

    /**
     * 获取我的收藏列表
     *
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<FavoriteVO> listMyFavorites(PageQuery query);

    /**
     * 添加收藏
     *
     * @param filmId 电影 ID
     */
    void addFavorite(Long filmId);

    /**
     * 取消收藏
     *
     * @param filmId 电影 ID
     */
    void removeFavorite(Long filmId);

    /**
     * 检查是否已收藏
     *
     * @param filmId 电影 ID
     * @return 是否已收藏
     */
    boolean isFavorite(Long filmId);
}
