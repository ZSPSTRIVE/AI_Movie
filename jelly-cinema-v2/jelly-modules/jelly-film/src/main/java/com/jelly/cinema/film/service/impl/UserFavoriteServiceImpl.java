package com.jelly.cinema.film.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.entity.UserFavorite;
import com.jelly.cinema.film.domain.vo.FavoriteVO;
import com.jelly.cinema.film.mapper.FilmMapper;
import com.jelly.cinema.film.mapper.UserFavoriteMapper;
import com.jelly.cinema.film.service.UserFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户收藏服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFavoriteServiceImpl implements UserFavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final FilmMapper filmMapper;

    @Override
    public PageResult<FavoriteVO> listMyFavorites(PageQuery query) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.orderByDesc(UserFavorite::getCreateTime);

        Page<UserFavorite> page = favoriteMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        List<FavoriteVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public void addFavorite(Long filmId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        // 检查电影是否存在
        Film film = filmMapper.selectById(filmId);
        if (film == null) {
            throw new ServiceException("电影不存在");
        }

        // 检查是否已收藏
        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.eq(UserFavorite::getFilmId, filmId);
        if (favoriteMapper.selectCount(wrapper) > 0) {
            throw new ServiceException("已收藏该电影");
        }

        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setFilmId(filmId);
        favoriteMapper.insert(favorite);

        log.info("用户 {} 收藏电影 {}", userId, filmId);
    }

    @Override
    public void removeFavorite(Long filmId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.eq(UserFavorite::getFilmId, filmId);
        favoriteMapper.delete(wrapper);

        log.info("用户 {} 取消收藏电影 {}", userId, filmId);
    }

    @Override
    public boolean isFavorite(Long filmId) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            return false;
        }

        LambdaQueryWrapper<UserFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFavorite::getUserId, userId);
        wrapper.eq(UserFavorite::getFilmId, filmId);
        return favoriteMapper.selectCount(wrapper) > 0;
    }

    private FavoriteVO toVO(UserFavorite favorite) {
        FavoriteVO vo = new FavoriteVO();
        vo.setId(favorite.getId());
        vo.setFilmId(favorite.getFilmId());
        vo.setCreateTime(favorite.getCreateTime());

        // 获取电影信息
        Film film = filmMapper.selectById(favorite.getFilmId());
        if (film != null) {
            vo.setTitle(film.getTitle());
            vo.setPoster(film.getCoverUrl());
            vo.setYear(film.getYear());
            vo.setRating(film.getRating());
        }

        return vo;
    }
}
