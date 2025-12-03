package com.jelly.cinema.film.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.entity.WatchHistory;
import com.jelly.cinema.film.domain.vo.WatchHistoryVO;
import com.jelly.cinema.film.mapper.FilmMapper;
import com.jelly.cinema.film.mapper.WatchHistoryMapper;
import com.jelly.cinema.film.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 观看历史服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchHistoryServiceImpl implements WatchHistoryService {

    private final WatchHistoryMapper historyMapper;
    private final FilmMapper filmMapper;

    @Override
    public PageResult<WatchHistoryVO> listMyHistory(PageQuery query) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<WatchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WatchHistory::getUserId, userId);
        wrapper.orderByDesc(WatchHistory::getWatchTime);

        Page<WatchHistory> page = historyMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        List<WatchHistoryVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void recordProgress(Long filmId, Integer progress) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        // 检查电影是否存在
        Film film = filmMapper.selectById(filmId);
        if (film == null) {
            throw new ServiceException("电影不存在");
        }

        // 查找是否已有记录
        LambdaQueryWrapper<WatchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WatchHistory::getUserId, userId);
        wrapper.eq(WatchHistory::getFilmId, filmId);
        WatchHistory history = historyMapper.selectOne(wrapper);

        if (history != null) {
            // 更新进度
            history.setProgress(progress);
            history.setWatchTime(LocalDateTime.now());
            historyMapper.updateById(history);
        } else {
            // 创建新记录
            history = new WatchHistory();
            history.setUserId(userId);
            history.setFilmId(filmId);
            history.setProgress(progress);
            history.setWatchTime(LocalDateTime.now());
            historyMapper.insert(history);
        }

        log.debug("用户 {} 观看电影 {} 进度 {}%", userId, filmId, progress);
    }

    @Override
    public void deleteHistory(Long id) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        WatchHistory history = historyMapper.selectById(id);
        if (history == null || !history.getUserId().equals(userId)) {
            throw new ServiceException("记录不存在");
        }

        historyMapper.deleteById(id);
        log.info("用户 {} 删除观看记录 {}", userId, id);
    }

    @Override
    public void clearHistory() {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<WatchHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WatchHistory::getUserId, userId);
        historyMapper.delete(wrapper);

        log.info("用户 {} 清空观看历史", userId);
    }

    private WatchHistoryVO toVO(WatchHistory history) {
        WatchHistoryVO vo = new WatchHistoryVO();
        vo.setId(history.getId());
        vo.setFilmId(history.getFilmId());
        vo.setProgress(history.getProgress());
        vo.setWatchTime(history.getWatchTime());

        // 获取电影信息
        Film film = filmMapper.selectById(history.getFilmId());
        if (film != null) {
            vo.setTitle(film.getTitle());
            vo.setPoster(film.getCoverUrl());
        }

        return vo;
    }
}
