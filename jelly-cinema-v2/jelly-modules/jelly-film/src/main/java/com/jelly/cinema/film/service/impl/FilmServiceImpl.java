package com.jelly.cinema.film.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PreDestroy;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    @Value("${tvbox.startup-sync.recommend-limit:100}")
    private int startupRecommendLimit;

    @Value("${tvbox.startup-sync.list-page-size:100}")
    private int startupListPageSize;

    @Value("${tvbox.startup-sync.max-list-pages:20}")
    private int startupMaxListPages;

    @Value("${tvbox.startup-sync.search-limit:20}")
    private int startupSearchLimit;

    @Value("${tvbox.startup-sync.play-url-prefetch-limit:30}")
    private int startupPlayUrlPrefetchLimit;

    @Value("${tvbox.startup-sync.search-keywords:电影,喜剧,电视剧,动漫,科幻,动作,爱情,悬疑,冒险,战争,综艺,纪录片,少儿,经典,高分,轻松,休闲,下饭,治愈,热血}")
    private String startupSearchKeywords;

    private static final String FILM_HOT_RANK_KEY = "jelly:film:hot:rank";
    private static final String FILM_PLAY_COUNT_KEY = "jelly:film:play:count:";
    private static final int SEARCH_LIMIT = 20;
    private static final int RAG_SYNC_LIMIT = 200;
    private static final int RAG_SYNC_TRIGGER_LIMIT = 100;

    /**
     * 本地缓存 - 分类信息
     */
    private final Cache<Long, Category> categoryCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private final ExecutorService ragSyncExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "film-rag-sync");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<RagSyncProgress> ragSyncProgressRef =
            new AtomicReference<>(RagSyncProgress.idle());

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

        int imported = importFromTvboxSearch(
                normalizedKeyword,
                SEARCH_LIMIT,
                new AtomicInteger(Math.min(SEARCH_LIMIT, startupPlayUrlPrefetchLimit)),
                buildSearchTags(normalizedKeyword)
        );
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
        Film existing = findExistingFilm(title, year);
        if (existing != null) {
            boolean changed = mergeExistingFilm(existing, data);
            if (changed) {
                filmMapper.updateById(existing);
                filmRagSyncService.syncFilm(existing);
            }
            return false;
        }

        long categoryId = resolveCategoryId(data, title);
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
        film.setDuration(toInteger(data.get("duration")));
        film.setCategoryId(categoryId);
        film.setTags(toStoredTags(buildTags(data, categoryId)));
        film.setStatus(0);
        film.setDeleted(0);

        filmMapper.insert(film);
        filmRagSyncService.syncFilm(film);
        return true;
    }

    @Override
    public int syncFilmsToRag(Integer limit) {
        List<Film> films = listFilmsForRagSync(sanitizeLimit(limit, RAG_SYNC_LIMIT, 1000));
        int synced = filmRagSyncService.syncFilms(films);
        log.info("从 MySQL 同步电影到 Python RAG 完成: total={}, success={}", films.size(), synced);
        return synced;
    }

    @Override
    public Map<String, Object> startSyncFilmsToRag(Integer limit) {
        RagSyncProgress current = ragSyncProgressRef.get();
        if (current.running()) {
            return current.toMap(true, false, "已有同步任务正在执行");
        }

        int safeLimit = sanitizeLimit(limit, RAG_SYNC_TRIGGER_LIMIT, 1000);
        List<Film> films = listFilmsForRagSync(safeLimit);
        if (films.isEmpty()) {
            RagSyncProgress empty = RagSyncProgress.finished(
                    "success",
                    safeLimit,
                    0,
                    0,
                    0,
                    "没有可同步的电影数据"
            );
            ragSyncProgressRef.set(empty);
            return empty.toMap(false, false, empty.message());
        }

        RagSyncProgress running = RagSyncProgress.running(safeLimit, films.size(), "已开始后台同步");
        ragSyncProgressRef.set(running);
        ragSyncExecutor.submit(() -> runRagSyncTask(films, safeLimit));
        return running.toMap(true, true, "已开始后台同步电影到 Python RAG");
    }

    @Override
    public Map<String, Object> getSyncFilmsToRagStatus() {
        return ragSyncProgressRef.get().toMap(false, ragSyncProgressRef.get().running(), null);
    }

    @Override
    public int warmupCatalogFromTvbox(int targetNewCount, boolean fullSweep) {
        int safeTarget = Math.max(targetNewCount, 0);
        if (safeTarget <= 0 && !fullSweep) {
            return 0;
        }

        int imported = 0;
        AtomicInteger playUrlBudget = new AtomicInteger(Math.max(0, startupPlayUrlPrefetchLimit));

        imported += importFromTvboxRecommend(Math.max(20, startupRecommendLimit), playUrlBudget);

        int listPages = resolveStartupListPages(safeTarget, fullSweep);
        for (int page = 1; page <= listPages; page++) {
            imported += importFromTvboxList(page, Math.max(20, startupListPageSize), playUrlBudget);
            if (!fullSweep && imported >= safeTarget) {
                return imported;
            }
        }

        for (String keyword : parseStartupKeywords()) {
            imported += importFromTvboxSearch(
                    keyword,
                    Math.max(10, startupSearchLimit),
                    playUrlBudget,
                    buildSearchTags(keyword)
            );
            if (!fullSweep && imported >= safeTarget) {
                break;
            }
        }

        return imported;
    }

    @PreDestroy
    public void shutdownRagSyncExecutor() {
        ragSyncExecutor.shutdownNow();
    }

    private FilmVO toVO(Film film) {
        FilmVO vo = BeanUtil.copyProperties(film, FilmVO.class);
        vo.setTags(parseStoredTags(film.getTags()));

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
                .like(Film::getDirector, keyword)
                .or()
                .like(Film::getTags, keyword)
                .or()
                .like(Film::getRegion, keyword);
        wrapper.eq(Film::getStatus, 0);
        wrapper.orderByDesc(Film::getPlayCount)
                .orderByDesc(Film::getRating)
                .orderByDesc(Film::getYear);
        return queryWithLimit(wrapper, limit);
    }

    private List<Film> listFilmsForRagSync(int limit) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getStatus, 0)
                .orderByDesc(Film::getUpdateTime)
                .orderByDesc(Film::getCreateTime);
        return queryWithLimit(wrapper, limit);
    }

    private void runRagSyncTask(List<Film> films, int limit) {
        int processed = 0;
        int success = 0;
        int failed = 0;
        int total = films.size();
        long startedAt = System.currentTimeMillis();

        ragSyncProgressRef.set(new RagSyncProgress(
                "running",
                true,
                limit,
                total,
                0,
                0,
                0,
                "正在同步 0/" + total,
                startedAt,
                null
        ));

        try {
            for (Film film : films) {
                boolean synced = filmRagSyncService.syncFilm(film);
                processed++;
                if (synced) {
                    success++;
                } else {
                    failed++;
                }
                ragSyncProgressRef.set(new RagSyncProgress(
                        "running",
                        true,
                        limit,
                        total,
                        processed,
                        success,
                        failed,
                        "正在同步 " + processed + "/" + total,
                        startedAt,
                        null
                ));
            }

            String state = failed == 0 ? "success" : (success > 0 ? "partial_success" : "failed");
            String message = failed == 0
                    ? "同步完成，已写入 " + success + " 条"
                    : "同步完成，成功 " + success + " 条，失败 " + failed + " 条";
            ragSyncProgressRef.set(new RagSyncProgress(
                    state,
                    false,
                    limit,
                    total,
                    processed,
                    success,
                    failed,
                    message,
                    startedAt,
                    System.currentTimeMillis()
            ));
            log.info("电影同步到 Python RAG 后台任务完成: total={}, success={}, failed={}", total, success, failed);
        } catch (Exception e) {
            ragSyncProgressRef.set(new RagSyncProgress(
                    "failed",
                    false,
                    limit,
                    total,
                    processed,
                    success,
                    failed + Math.max(0, total - processed),
                    "同步异常中断: " + e.getMessage(),
                    startedAt,
                    System.currentTimeMillis()
            ));
            log.error("电影同步到 Python RAG 后台任务失败", e);
        }
    }

    private int importFromTvboxRecommend(int limit, AtomicInteger playUrlBudget) {
        Object data = requestTvboxData("/recommend", Map.of("limit", limit));
        int imported = importFromTvboxPayloads(data, limit, Set.of("电影", "推荐"), playUrlBudget);
        log.info("TVBox 推荐补库完成: imported={}", imported);
        return imported;
    }

    private int importFromTvboxList(int page, int pageSize, AtomicInteger playUrlBudget) {
        Object data = requestTvboxData("/list", Map.of("page", page, "pageSize", pageSize));
        Object listData = data instanceof Map<?, ?> dataMap ? dataMap.get("list") : null;
        int imported = importFromTvboxPayloads(listData, pageSize, Set.of("电影", "片库"), playUrlBudget);
        log.info("TVBox 列表补库完成: page={}, pageSize={}, imported={}", page, pageSize, imported);
        return imported;
    }

    private int importFromTvboxSearch(
            String keyword,
            int limit,
            AtomicInteger playUrlBudget,
            Set<String> fixedTags
    ) {
        try {
            Object data = requestTvboxData("/search", Map.of("keyword", keyword));
            int imported = importFromTvboxPayloads(data, limit, fixedTags, playUrlBudget);
            log.info("TVBox 搜索补库完成: keyword={}, imported={}", keyword, imported);
            return imported;
        } catch (Exception e) {
            log.warn("TVBox 搜索补库失败: keyword={}, err={}", keyword, e.getMessage());
            return 0;
        }
    }

    private int importFromTvboxPayloads(
            Object rawData,
            int limit,
            Set<String> fixedTags,
            AtomicInteger playUrlBudget
    ) {
        if (!(rawData instanceof List<?> films) || films.isEmpty()) {
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
            mergePayloadTags(payload, fixedTags);

            String tvboxId = stringValue(payload.get("id"));
            if (StrUtil.isNotBlank(tvboxId) && shouldPrefetchPlayUrl(playUrlBudget)) {
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
    }

    private Object requestTvboxData(String path, Map<String, Object> queryParams) {
        try {
            String url = buildTvboxUrl(path, queryParams);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            return response.getBody().get("data");
        } catch (Exception e) {
            log.warn("请求 TVBox 接口失败: path={}, err={}", path, e.getMessage());
            return null;
        }
    }

    private String buildTvboxUrl(String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(tvboxProxyBaseUrl + path);
        queryParams.forEach((key, value) -> {
            if (value != null && StrUtil.isNotBlank(String.valueOf(value))) {
                builder.queryParam(key, value);
            }
        });
        return builder.toUriString();
    }

    private Film findExistingFilm(String title, Integer year) {
        LambdaQueryWrapper<Film> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Film::getTitle, title);
        if (year != null && year > 1900) {
            wrapper.eq(Film::getYear, year);
        }
        wrapper.last("LIMIT 1");
        List<Film> films = filmMapper.selectList(wrapper);
        return films.isEmpty() ? null : films.get(0);
    }

    private boolean mergeExistingFilm(Film existing, Map<String, Object> data) {
        boolean changed = false;

        changed |= replaceIfBetter(existing::getCoverUrl, existing::setCoverUrl, stringValue(data.get("coverUrl")));
        changed |= replaceIfLonger(existing::getDescription, existing::setDescription, stringValue(data.get("description")));
        changed |= replaceIfBetter(existing::getDirector, existing::setDirector, stringValue(data.get("director")));
        changed |= replaceIfBetter(existing::getActors, existing::setActors, stringValue(data.get("actors")));
        changed |= replaceIfBetter(existing::getRegion, existing::setRegion, stringValue(data.get("region")));
        changed |= replaceIfBetter(existing::getVideoUrl, existing::setVideoUrl, stringValue(data.get("videoUrl")));

        Integer year = toInteger(data.get("year"));
        if (existing.getYear() == null && year != null && year > 1900) {
            existing.setYear(year);
            changed = true;
        }

        Integer duration = toInteger(data.get("duration"));
        if ((existing.getDuration() == null || existing.getDuration() <= 0) && duration != null && duration > 0) {
            existing.setDuration(duration);
            changed = true;
        }

        Double rating = toDouble(data.get("rating"), null);
        if (rating != null && rating > 0 && (existing.getRating() == null || rating > existing.getRating())) {
            existing.setRating(rating);
            changed = true;
        }

        Long playCount = toLong(data.get("playCount"), null);
        if (playCount != null && playCount > 0 && (existing.getPlayCount() == null || playCount > existing.getPlayCount())) {
            existing.setPlayCount(playCount);
            changed = true;
        }

        long resolvedCategoryId = resolveCategoryId(data, existing.getTitle());
        if ((existing.getCategoryId() == null || existing.getCategoryId() == 0L) && resolvedCategoryId > 0) {
            existing.setCategoryId(resolvedCategoryId);
            changed = true;
        }

        String mergedTags = toStoredTags(mergeTagCollections(
                parseStoredTags(existing.getTags()),
                buildTags(data, existing.getCategoryId() != null ? existing.getCategoryId() : resolvedCategoryId)
        ));
        if (!StrUtil.equals(StrUtil.blankToDefault(existing.getTags(), ""), mergedTags)) {
            existing.setTags(mergedTags);
            changed = true;
        }

        if (existing.getStatus() == null || existing.getStatus() != 0) {
            existing.setStatus(0);
            changed = true;
        }

        return changed;
    }

    private long resolveCategoryId(Map<String, Object> data, String title) {
        String contentType = stringValue(data.get("contentType")).toLowerCase();
        if (contentType.contains("tv_series") || contentType.contains("series") || contentType.contains("tv")) {
            return 2L;
        }
        if (title != null && title.matches(".*(第.+季|全\\d+集|更新至\\d+集).*")) {
            return 2L;
        }
        Collection<String> tags = extractTagValues(data.get("tags"));
        if (tags.stream().anyMatch(tag -> tag.contains("电视剧") || tag.contains("剧集") || tag.contains("连续剧"))) {
            return 2L;
        }
        return 1L;
    }

    private List<String> buildTags(Map<String, Object> data, long categoryId) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(extractTagValues(data.get("tags")));
        tags.addAll(extractTagValues(data.get("keywords")));

        String sourceName = stringValue(data.get("sourceName"));
        if (StrUtil.isBlank(sourceName)) {
            sourceName = stringValue(data.get("sourceKey"));
        }
        if (StrUtil.isNotBlank(sourceName)) {
            tags.add(sourceName);
        }

        String region = stringValue(data.get("region"));
        if (StrUtil.isNotBlank(region)) {
            tags.add(region);
        }

        if (categoryId == 2L) {
            tags.add("电视剧");
        } else {
            tags.add("电影");
        }

        String contentType = stringValue(data.get("contentType")).toLowerCase();
        if (contentType.contains("anime") || contentType.contains("animation") || contentType.contains("cartoon")) {
            tags.add("动漫");
        }
        if (contentType.contains("variety") || contentType.contains("show")) {
            tags.add("综艺");
        }
        if (contentType.contains("documentary")) {
            tags.add("纪录片");
        }

        return tags.stream()
                .map(this::normalizeTag)
                .filter(StrUtil::isNotBlank)
                .limit(16)
                .collect(Collectors.toList());
    }

    private void mergePayloadTags(Map<String, Object> payload, Set<String> fixedTags) {
        LinkedHashSet<String> merged = mergeTagCollections(extractTagValues(payload.get("tags")), fixedTags);
        if (!merged.isEmpty()) {
            payload.put("tags", new ArrayList<>(merged));
        }
    }

    private LinkedHashSet<String> mergeTagCollections(Collection<String> first, Collection<String> second) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (first != null) {
            first.stream().map(this::normalizeTag).filter(StrUtil::isNotBlank).forEach(tags::add);
        }
        if (second != null) {
            second.stream().map(this::normalizeTag).filter(StrUtil::isNotBlank).forEach(tags::add);
        }
        return tags;
    }

    private Collection<String> extractTagValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .map(this::normalizeTag)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return List.of();
        }

        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                return JSONUtil.parseArray(text).stream()
                        .map(String::valueOf)
                        .map(this::normalizeTag)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toList());
            } catch (Exception ignored) {
                // Fall through to comma-based parsing.
            }
        }

        return List.of(text.split("[,，/]"))
                .stream()
                .map(this::normalizeTag)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private List<String> parseStoredTags(String rawTags) {
        if (StrUtil.isBlank(rawTags)) {
            return List.of();
        }
        return new ArrayList<>(mergeTagCollections(extractTagValues(rawTags), List.of()));
    }

    private String toStoredTags(Collection<String> tags) {
        LinkedHashSet<String> normalized = mergeTagCollections(tags, List.of());
        return normalized.isEmpty() ? "" : JSONUtil.toJsonStr(new ArrayList<>(normalized));
    }

    private String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String cleaned = tag.trim()
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "");
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }

    private int resolveStartupListPages(int targetNewCount, boolean fullSweep) {
        int pageSize = Math.max(20, startupListPageSize);
        int desiredPages = (int) Math.ceil((double) Math.max(targetNewCount, pageSize) / pageSize);
        if (!fullSweep) {
            desiredPages = Math.max(2, desiredPages * 2);
        }
        return Math.max(1, Math.min(Math.max(1, startupMaxListPages), desiredPages));
    }

    private List<String> parseStartupKeywords() {
        return List.of(startupSearchKeywords.split("[,，]"))
                .stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<String> buildSearchTags(String keyword) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        String normalized = normalizeTag(keyword);
        if (StrUtil.isBlank(normalized)) {
            return tags;
        }

        tags.add(normalized);
        if (normalized.contains("电影")) {
            tags.add("电影");
        }
        if (normalized.contains("剧")) {
            tags.add("电视剧");
        }
        if (normalized.contains("动漫") || normalized.contains("动画")) {
            tags.add("动漫");
        }
        if (normalized.contains("综艺")) {
            tags.add("综艺");
        }
        if (normalized.contains("纪录")) {
            tags.add("纪录片");
        }
        if (normalized.contains("喜剧")) {
            tags.add("喜剧");
        }
        if (normalized.contains("科幻")) {
            tags.add("科幻");
        }
        if (normalized.contains("动作")) {
            tags.add("动作");
        }
        if (normalized.contains("爱情")) {
            tags.add("爱情");
        }
        if (normalized.contains("悬疑")) {
            tags.add("悬疑");
        }
        if (normalized.contains("休闲") || normalized.contains("轻松") || normalized.contains("下饭") || normalized.contains("治愈")) {
            tags.add("休闲");
            tags.add("轻松");
        }
        return tags;
    }

    private boolean shouldPrefetchPlayUrl(AtomicInteger budget) {
        while (true) {
            int current = budget.get();
            if (current <= 0) {
                return false;
            }
            if (budget.compareAndSet(current, current - 1)) {
                return true;
            }
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

    private boolean replaceIfBetter(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, String candidate) {
        if (StrUtil.isBlank(candidate)) {
            return false;
        }
        String existing = getter.get();
        if (StrUtil.isBlank(existing)) {
            setter.accept(candidate);
            return true;
        }
        return false;
    }

    private boolean replaceIfLonger(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, String candidate) {
        if (StrUtil.isBlank(candidate)) {
            return false;
        }
        String existing = getter.get();
        if (StrUtil.isBlank(existing) || candidate.length() > existing.length()) {
            setter.accept(candidate);
            return true;
        }
        return false;
    }

    private record RagSyncProgress(
            String state,
            boolean running,
            int limit,
            int total,
            int processed,
            int success,
            int failed,
            String message,
            long startedAt,
            Long finishedAt
    ) {
        private static RagSyncProgress idle() {
            return new RagSyncProgress("idle", false, 0, 0, 0, 0, 0, "尚未开始同步", 0L, null);
        }

        private static RagSyncProgress running(int limit, int total, String message) {
            return new RagSyncProgress(
                    "running",
                    true,
                    limit,
                    total,
                    0,
                    0,
                    0,
                    message,
                    System.currentTimeMillis(),
                    null
            );
        }

        private static RagSyncProgress finished(
                String state,
                int limit,
                int total,
                int success,
                int failed,
                String message
        ) {
            long now = System.currentTimeMillis();
            return new RagSyncProgress(state, false, limit, total, total, success, failed, message, now, now);
        }

        private Map<String, Object> toMap(boolean accepted, boolean started, String overrideMessage) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", !"failed".equals(state));
            result.put("accepted", accepted);
            result.put("started", started);
            result.put("running", running);
            result.put("state", state);
            result.put("limit", limit);
            result.put("total", total);
            result.put("processed", processed);
            result.put("synced", success);
            result.put("failed", failed);
            result.put("message", StrUtil.blankToDefault(overrideMessage, message));
            result.put("startedAt", startedAt);
            result.put("finishedAt", finishedAt);
            return result;
        }
    }
}
