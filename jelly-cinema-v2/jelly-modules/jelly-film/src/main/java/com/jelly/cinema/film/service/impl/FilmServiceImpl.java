package com.jelly.cinema.film.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.film.domain.dto.FilmQueryDTO;
import com.jelly.cinema.film.domain.entity.Category;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.mapper.CategoryMapper;
import com.jelly.cinema.film.mapper.FilmMapper;
import com.jelly.cinema.film.service.FilmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 电影服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilmServiceImpl implements FilmService {

    private final FilmMapper filmMapper;
    private final CategoryMapper categoryMapper;
    private final RedisService redisService;

    private static final String FILM_HOT_RANK_KEY = "jelly:film:hot:rank";
    private static final String FILM_PLAY_COUNT_KEY = "jelly:film:play:count:";

    /**
     * 本地缓存 - 分类信息
     */
    private final Cache<Long, Category> categoryCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Override
    public PageResult<FilmVO> list(FilmQueryDTO dto) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        
        // 条件查询
        wrapper.like(StrUtil.isNotBlank(dto.getKeyword()), Film::getTitle, dto.getKeyword());
        wrapper.eq(dto.getCategoryId() != null, Film::getCategoryId, dto.getCategoryId());
        wrapper.eq(dto.getYear() != null, Film::getYear, dto.getYear());
        wrapper.eq(StrUtil.isNotBlank(dto.getRegion()), Film::getRegion, dto.getRegion());
        wrapper.eq(Film::getStatus, 0);

        // 排序
        switch (dto.getSort()) {
            case "new" -> wrapper.orderByDesc(Film::getCreateTime);
            case "rating" -> wrapper.orderByDesc(Film::getRating);
            default -> wrapper.orderByDesc(Film::getPlayCount);
        }

        // 分页查询
        Page<Film> page = filmMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()),
                wrapper
        );

        // 转换 VO
        List<FilmVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), dto.getPageNum(), dto.getPageSize());
    }

    @Override
    public FilmVO getDetail(Long id) {
        Film film = filmMapper.selectById(id);
        if (film == null) {
            throw new ServiceException("电影不存在");
        }
        return toVO(film);
    }

    @Override
    public List<FilmVO> search(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return List.of();
        }

        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Film::getTitle, keyword)
                .or()
                .like(Film::getDescription, keyword)
                .or()
                .like(Film::getActors, keyword)
                .or()
                .like(Film::getDirector, keyword);
        wrapper.eq(Film::getStatus, 0);
        wrapper.orderByDesc(Film::getPlayCount);
        wrapper.last("LIMIT 20");

        return filmMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FilmVO> getRecommend(Integer size) {
        // 简单推荐：高评分 + 随机
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0);
        wrapper.ge(Film::getRating, 7.0);
        wrapper.orderByDesc(Film::getRating);
        wrapper.last("LIMIT " + (size != null ? size : 10));

        return filmMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FilmVO> getHotRank(Integer size) {
        int limit = size != null ? size : 10;

        try {
            // 尝试从 Redis ZSet 缓存获取（按分数降序）
            Set<Object> cachedIds = redisService.zReverseRange(FILM_HOT_RANK_KEY, 0, limit - 1);
            if (cachedIds != null && !cachedIds.isEmpty()) {
                return cachedIds.stream()
                        .map(obj -> {
                            try {
                                return getDetail(Long.valueOf(obj.toString()));
                            } catch (Exception e) {
                                log.warn("获取电影详情失败: {}", obj, e);
                                return null;
                            }
                        })
                        .filter(vo -> vo != null)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("从 Redis 获取热门榜单失败，fallback 到数据库查询", e);
        }

        // 缓存未命中或 Redis 异常，从数据库查询
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0);
        wrapper.orderByDesc(Film::getPlayCount);
        wrapper.last("LIMIT " + limit);

        return filmMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void incrementPlayCount(Long id) {
        // 使用 Redis 累计播放量
        String key = FILM_PLAY_COUNT_KEY + id;
        redisService.increment(key);

        // 更新热门榜单分数
        redisService.zIncrementScore(FILM_HOT_RANK_KEY, id, 1);

        log.debug("电影播放量 +1, id={}", id);
    }

    @Override
    public List<FilmVO> getFilmsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        // 批量查询
        List<Film> films = filmMapper.selectBatchIds(ids);

        // 转换为 Map 方便按 ID 顺序排列
        Map<Long, FilmVO> filmMap = films.stream()
                .collect(Collectors.toMap(Film::getId, this::toVO));

        // 保持传入的 ID 顺序
        return ids.stream()
                .map(filmMap::get)
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    /**
     * 转换为 VO
     */
    private FilmVO toVO(Film film) {
        FilmVO vo = BeanUtil.copyProperties(film, FilmVO.class);

        // 解析标签 JSON
        if (StrUtil.isNotBlank(film.getTags())) {
            vo.setTags(JSONUtil.toList(film.getTags(), String.class));
        }

        // 获取分类名称
        if (film.getCategoryId() != null) {
            Category category = categoryCache.get(film.getCategoryId(), 
                    id -> categoryMapper.selectById(id));
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        return vo;
    }
}
