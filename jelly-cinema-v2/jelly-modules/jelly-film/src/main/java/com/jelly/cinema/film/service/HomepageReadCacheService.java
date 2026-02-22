package com.jelly.cinema.film.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jelly.cinema.film.domain.vo.HomepageContentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Read-side cache for homepage APIs.
 */
@Service
@RequiredArgsConstructor
public class HomepageReadCacheService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final long CACHE_TTL_MINUTES = 3;

    private final HomepageContentService homepageContentService;

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    public List<HomepageContentVO> getRecommend(Integer limit) {
        int safeLimit = normalizeLimit(limit, 18);
        return cachedList("recommend:" + safeLimit, () -> homepageContentService.getRecommendList(safeLimit));
    }

    public List<HomepageContentVO> getHot(Integer limit) {
        int safeLimit = normalizeLimit(limit, 10);
        return cachedList("hot:" + safeLimit, () -> homepageContentService.getHotList(safeLimit));
    }

    public List<HomepageContentVO> getMovies(Integer limit) {
        int safeLimit = normalizeLimit(limit, 18);
        return cachedList("movies:" + safeLimit, () -> homepageContentService.getMovieList(safeLimit));
    }

    public List<HomepageContentVO> getTvSeries(Integer limit) {
        int safeLimit = normalizeLimit(limit, 12);
        return cachedList("tv-series:" + safeLimit, () -> homepageContentService.getTvSeriesList(safeLimit));
    }

    public List<HomepageContentVO> getAiBest(Integer limit) {
        int safeLimit = normalizeLimit(limit, 6);
        return cachedList("ai-best:" + safeLimit, () -> homepageContentService.getAiBestList(safeLimit));
    }

    public List<HomepageContentVO> getNew(Integer limit) {
        int safeLimit = normalizeLimit(limit, 12);
        return cachedList("new:" + safeLimit, () -> homepageContentService.getNewList(safeLimit));
    }

    public List<HomepageContentVO> getTrending(Integer limit) {
        int safeLimit = normalizeLimit(limit, 8);
        return cachedList("trending:" + safeLimit, () -> homepageContentService.getTrendingList(safeLimit));
    }

    @SuppressWarnings("unchecked")
    public Map<String, List<HomepageContentVO>> getSections() {
        return (Map<String, List<HomepageContentVO>>) cache.get("sections:full", key -> {
            Map<String, List<HomepageContentVO>> sections = homepageContentService.getSectionedContent();
            return deepCopySections(sections);
        });
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    @SuppressWarnings("unchecked")
    private List<HomepageContentVO> cachedList(String key, Supplier<List<HomepageContentVO>> loader) {
        return (List<HomepageContentVO>) cache.get(key, k -> {
            List<HomepageContentVO> list = loader.get();
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(list));
        });
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        int value = limit == null ? defaultValue : limit;
        if (value < MIN_LIMIT) {
            return defaultValue;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private Map<String, List<HomepageContentVO>> deepCopySections(Map<String, List<HomepageContentVO>> source) {
        Map<String, List<HomepageContentVO>> copied = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return copied;
        }
        for (Map.Entry<String, List<HomepageContentVO>> entry : source.entrySet()) {
            List<HomepageContentVO> value = entry.getValue() == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            copied.put(entry.getKey(), value);
        }
        return copied;
    }
}
