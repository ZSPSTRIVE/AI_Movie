package com.jelly.cinema.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.dto.FilmCreateDTO;
import com.jelly.cinema.admin.domain.entity.Film;
import com.jelly.cinema.admin.mapper.AdminFilmMapper;
import com.jelly.cinema.admin.service.AdminFilmService;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 电影管理服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFilmServiceImpl implements AdminFilmService {

    private final AdminFilmMapper filmMapper;

    @Override
    public PageResult<Film> list(PageQuery query, String keyword, Long categoryId) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(keyword), Film::getTitle, keyword);
        wrapper.eq(categoryId != null, Film::getCategoryId, categoryId);
        wrapper.orderByDesc(Film::getCreateTime);

        Page<Film> page = filmMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        return PageResult.build(page.getRecords(), page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public Film getById(Long id) {
        return filmMapper.selectById(id);
    }

    @Override
    public Long saveOrUpdate(FilmCreateDTO dto) {
        Film film;
        if (dto.getId() != null) {
            // 更新
            film = filmMapper.selectById(dto.getId());
            if (film == null) {
                throw new ServiceException("电影不存在");
            }
            BeanUtil.copyProperties(dto, film, "id", "playCount", "status");
            filmMapper.updateById(film);
            log.info("电影更新: id={}", film.getId());
        } else {
            // 创建
            film = BeanUtil.copyProperties(dto, Film.class);
            film.setPlayCount(0L);
            film.setStatus(0);
            filmMapper.insert(film);
            log.info("电影创建: id={}, title={}", film.getId(), film.getTitle());
        }
        return film.getId();
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        Film film = filmMapper.selectById(id);
        if (film == null) {
            throw new ServiceException("电影不存在");
        }

        film.setStatus(status);
        filmMapper.updateById(film);
        log.info("电影状态更新: id={}, status={}", id, status);
    }

    @Override
    public void delete(Long id) {
        filmMapper.deleteById(id);
        log.info("电影删除: id={}", id);
    }

    @Override
    public Long countTotal() {
        return filmMapper.countTotal();
    }

    @Override
    public Long sumPlayCount() {
        return filmMapper.sumPlayCount();
    }
}
