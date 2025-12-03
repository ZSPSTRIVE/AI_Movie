package com.jelly.cinema.film.service;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.film.domain.vo.WatchHistoryVO;

/**
 * 观看历史服务接口
 *
 * @author Jelly Cinema
 */
public interface WatchHistoryService {

    /**
     * 获取我的观看历史
     *
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<WatchHistoryVO> listMyHistory(PageQuery query);

    /**
     * 记录/更新观看进度
     *
     * @param filmId   电影 ID
     * @param progress 观看进度（百分比 0-100）
     */
    void recordProgress(Long filmId, Integer progress);

    /**
     * 删除观看记录
     *
     * @param id 记录 ID
     */
    void deleteHistory(Long id);

    /**
     * 清空观看历史
     */
    void clearHistory();
}
