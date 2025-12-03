package com.jelly.cinema.admin.service;

import com.jelly.cinema.admin.domain.dto.FilmCreateDTO;
import com.jelly.cinema.admin.domain.entity.Film;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;

/**
 * 电影管理服务接口
 *
 * @author Jelly Cinema
 */
public interface AdminFilmService {

    /**
     * 分页查询电影
     */
    PageResult<Film> list(PageQuery query, String keyword, Long categoryId);

    /**
     * 获取电影详情
     */
    Film getById(Long id);

    /**
     * 创建/更新电影
     */
    Long saveOrUpdate(FilmCreateDTO dto);

    /**
     * 上架/下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * 删除电影
     */
    void delete(Long id);

    /**
     * 统计电影数
     */
    Long countTotal();

    /**
     * 统计播放量
     */
    Long sumPlayCount();
}
