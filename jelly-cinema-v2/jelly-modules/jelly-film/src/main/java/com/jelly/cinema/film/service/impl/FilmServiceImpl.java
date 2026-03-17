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
import com.jelly.cinema.film.service.FilmRagSyncService;
import com.jelly.cinema.film.service.FilmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 电影服务实现
 *
 * API 搜索链路统一为：
 * TVBox -> MySQL t_film -> Python RAG。
 * 前端和 AI 侧都优先查数据库，数据库缺数据时再触发 TVBox 拉取并回写。
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
    private final RestTemplate restTemplate;
    private final FilmRagSyncService filmRagSyncService;

    @Value("${tvbox.proxy.base-url:http://localhost:3001/api/tvbox}")
    private String tvboxProxyBaseUrl;

    private static final String FILM_HOT_RANK_KEY = "jelly:film:hot:rank";
    private static final String FILM_PLAY_COUNT_KEY = "jelly:film:play:count:";
    private static final int SEARCH_LIMIT = 20;
    private static final int RAG_SYNC_LIMIT = 200;

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

        wrapper.like(StrUtil.isNotBlank(dto.getKeyword()), Film::getTitle, dto.getKeyword());
        wrapper.eq(dto.getCategoryId() != null, Film::getCategoryId, dto.getCategoryId());
        wrapper.eq(dto.getYear() != null, Film::getYear, dto.getYear());
        wrapper.eq(StrUtil.isNotBlank(dto.getRegion()), Film::getRegion, dto.getRegion());
        wrapper.eq(Film::getStatus, 0);

        switch (dto.getSort()) {
            case "new" -> wrapper.orderByDesc(Film::getCreateTime);
            case "rating" -> wrapper.orderByDesc(Film::getRating);
            default -> wrapper.orderByDesc(Film::getPlayCount);
        }

        Page<Film> page = filmMapper.selectPage(
                new Page<>(dto.getPageNum(), dto.getPageSize()),
                wrapper
        );

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

        String normalizedKeyword = keyword.trim();
        List<Film> localFilms = searchFromDatabase(normalizedKeyword, SEARCH_LIMIT);
        if (localFilms.size() >= 8) {
            return localFilms.stream().map(this::toVO).collect(Collectors.toList());
        }

        int imported = importFromTvboxSearch(normalizedKeyword, SEARCH_LIMIT);
        if (imported > 0) {
            log.info("电影搜索触发 TVBox 补库完成: keyword={}, imported={}", normalizedKeyword, imported);
        }

        return searchFromDatabase(normalizedKeyword, SEARCH_LIMIT).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FilmVO> getRecommend(Integer size) {
        int safeSize = sanitizeLimit(size, 10, 50);
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0);
        wrapper.ge(Film::getRating, 7.0);
        wrapper.orderByDesc(Film::getRating);

        return queryWithLimit(wrapper, safeSize).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<FilmVO> getHotRank(Integer size) {
        int limit = size != null ? size : 10;

        try {
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
            log.warn("从 Redis 获取热门榜单失败，回退数据库查询", e);
        }

        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0);
        wrapper.orderByDesc(Film::getPlayCount);

        return queryWithLimit(wrapper, sanitizeLimit(limit, 10, 50)).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void incrementPlayCount(Long id) {
        String key = FILM_PLAY_COUNT_KEY + id;
        redisService.increment(key);
        redisService.zIncrementScore(FILM_HOT_RANK_KEY, id, 1);
        log.debug("电影播放量 +1, id={}", id);
    }

    @Override
    public List<FilmVO> getFilmsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Film> films = filmMapper.selectBatchIds(ids);
        Map<Long, FilmVO> filmMap = films.stream()
                .collect(Collectors.toMap(Film::getId, this::toVO));

        return ids.stream()
                .map(filmMap::get)
                .filter(vo -> vo != null)
                .collect(Collectors.toList());
    }

    @Override
    public boolean saveFromTvbox(Map<String, Object> data) {
        String title = stringValue(data.get("title"));
        if (StrUtil.isBlank(title)) {
            return false;
        }

        Integer year = toInteger(data.get("year"));
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getTitle, title);
        if (year != null && year > 1900) {
            wrapper.eq(Film::getYear, year);
        }
        if (filmMapper.exists(wrapper)) {
            return false;
        }

        Film film = new Film();
        film.setTitle(title);
        film.setCoverUrl(stringValue(data.get("coverUrl")));
        film.setDescription(stringValue(data.get("description")));
        film.setRating(toDouble(data.get("rating"), 7.0));
        film.setYear(year);
        film.setRegion(stringValue(data.get("region")));
        film.setDirector(stringValue(data.get("director")));
        film.setActors(stringValue(data.get("actors")));
        film.setVideoUrl(stringValue(data.get("videoUrl")));
        film.setPlayCount(toLong(data.get("playCount"), 0L));

        long categoryId = 1L;
        String contentType = stringValue(data.get("contentType"));
        if ("tv_series".equalsIgnoreCase(contentType) || title.matches(".*(第.*季|全.*集).*")) {
            categoryId = 2L;
        }

        film.setCategoryId(categoryId);
        film.setStatus(0);
        film.setDeleted(0);

        filmMapper.insert(film);
        filmRagSyncService.syncFilm(film);
        return true;
    }

    @Override
    public int syncFilmsToRag(Integer limit) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0)
                .orderByDesc(Film::getUpdateTime)
                .orderByDesc(Film::getCreateTime);
        List<Film> films = queryWithLimit(wrapper, sanitizeLimit(limit, RAG_SYNC_LIMIT, 1000));
        int synced = filmRagSyncService.syncFilms(films);
        log.info("从 MySQL 同步电影到 Python RAG 完成: total={}, success={}", films.size(), synced);
        return synced;
    }

    private FilmVO toVO(Film film) {
        FilmVO vo = BeanUtil.copyProperties(film, FilmVO.class);

        if (StrUtil.isNotBlank(film.getTags())) {
            vo.setTags(JSONUtil.toList(film.getTags(), String.class));
        }

        if (film.getCategoryId() != null) {
            Category category = categoryCache.get(film.getCategoryId(), id -> categoryMapper.selectById(id));
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }

        return vo;
    }

    private List<Film> searchFromDatabase(String keyword, int limit) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Film::getTitle, keyword)
                .or()
                .like(Film::getDescription, keyword)
                .or()
                .like(Film::getActors, keyword)
                .or()
                .like(Film::getDirector, keyword);
        wrapper.eq(Film::getStatus, 0);
        wrapper.orderByDesc(Film::getPlayCount)
                .orderByDesc(Film::getRating)
                .orderByDesc(Film::getYear);
        return queryWithLimit(wrapper, limit);
    }

    @SuppressWarnings("unchecked")
    private int importFromTvboxSearch(String keyword, int limit) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(tvboxProxyBaseUrl + "/search")
                    .queryParam("keyword", keyword)
                    .toUriString();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return 0;
            }

            Object data = response.getBody().get("data");
            if (!(data instanceof List<?> films)) {
                return 0;
            }

            int imported = 0;
            int size = Math.min(limit, films.size());
            for (int i = 0; i < size; i++) {
                Object item = films.get(i);
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Map<String, Object> payload = new HashMap<>();
                rawMap.forEach((key, value) -> payload.put(String.valueOf(key), value));

                String tvboxId = stringValue(payload.get("id"));
                if (StrUtil.isNotBlank(tvboxId)) {
                    String playUrl = fetchTvboxPlayUrl(tvboxId);
                    if (StrUtil.isNotBlank(playUrl)) {
                        payload.put("videoUrl", playUrl);
                    }
                }

                if (saveFromTvbox(payload)) {
                    imported++;
                }
            }
            return imported;
        } catch (Exception e) {
            log.warn("TVBox 搜索补库失败: keyword={}, err={}", keyword, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchTvboxPlayUrl(String tvboxId) {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(tvboxProxyBaseUrl + "/play/{id}", Map.class, tvboxId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return "";
            }

            Object data = response.getBody().get("data");
            if (data instanceof Map<?, ?> playMap) {
                return stringValue(playMap.get("playUrl"));
            }
        } catch (Exception e) {
            log.debug("获取 TVBox 播放地址失败: id={}, err={}", tvboxId, e.getMessage());
        }
        return "";
    }

    private List<Film> queryWithLimit(LambdaQueryWrapper<Film> wrapper, int limit) {
        Page<Film> page = filmMapper.selectPage(new Page<>(1, limit, false), wrapper);
        return page.getRecords();
    }

    private int sanitizeLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maxValue);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Long toLong(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Double toDouble(Object value, Double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
