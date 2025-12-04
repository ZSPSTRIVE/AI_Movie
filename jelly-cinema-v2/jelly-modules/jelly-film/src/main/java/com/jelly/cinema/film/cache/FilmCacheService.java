package com.jelly.cinema.film.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jelly.cinema.film.domain.entity.Film;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.mapper.FilmMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 电影缓存服务
 * 
 * 实现多级缓存：
 * L1: Caffeine 本地缓存（毫秒级响应）
 * L2: Redis 分布式缓存（支持集群）
 * 
 * 缓存保护：
 * - BloomFilter 防止缓存穿透
 * - 互斥锁防止缓存击穿
 * - 随机过期时间防止缓存雪崩
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilmCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final FilmMapper filmMapper;

    /**
     * 本地缓存：电影详情
     */
    private Cache<Long, FilmVO> filmDetailCache;

    /**
     * 本地缓存：热门电影列表
     */
    private Cache<String, List<FilmVO>> hotFilmCache;

    /**
     * BloomFilter：电影 ID
     */
    private RBloomFilter<Long> filmIdBloomFilter;

    /**
     * Redis Key 前缀
     */
    private static final String FILM_DETAIL_KEY = "film:detail:";
    private static final String FILM_HOT_KEY = "film:hot:";
    private static final String FILM_CATEGORY_KEY = "film:category:";
    private static final String FILM_SEARCH_KEY = "film:search:";
    
    /**
     * 空值缓存 Key
     */
    private static final String NULL_VALUE_KEY = "film:null:";

    /**
     * 缓存过期时间
     */
    private static final long DETAIL_EXPIRE_SECONDS = 3600;      // 1 小时
    private static final long HOT_EXPIRE_SECONDS = 300;          // 5 分钟
    private static final long CATEGORY_EXPIRE_SECONDS = 1800;    // 30 分钟
    private static final long NULL_EXPIRE_SECONDS = 60;          // 空值 1 分钟

    @PostConstruct
    public void init() {
        log.info("===== 初始化电影缓存服务 =====");

        // 初始化本地缓存
        filmDetailCache = Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(10000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()
                .build();

        hotFilmCache = Caffeine.newBuilder()
                .initialCapacity(10)
                .maximumSize(100)
                .expireAfterWrite(Duration.ofMinutes(2))
                .recordStats()
                .build();

        // 初始化 BloomFilter
        filmIdBloomFilter = redissonClient.getBloomFilter("bloom:film:id");
        filmIdBloomFilter.tryInit(1000000, 0.01); // 预期 100 万数据，误判率 1%

        // 预热 BloomFilter
        warmUpBloomFilter();

        log.info("===== 电影缓存服务初始化完成 =====");
    }

    /**
     * 预热 BloomFilter
     */
    private void warmUpBloomFilter() {
        try {
            List<Long> filmIds = filmMapper.selectAllIds();
            if (filmIds != null && !filmIds.isEmpty()) {
                for (Long id : filmIds) {
                    filmIdBloomFilter.add(id);
                }
                log.info("BloomFilter 预热完成: {} 条电影 ID", filmIds.size());
            }
        } catch (Exception e) {
            log.warn("BloomFilter 预热失败: {}", e.getMessage());
        }
    }

    /**
     * 添加电影 ID 到 BloomFilter
     */
    public void addToBloomFilter(Long filmId) {
        filmIdBloomFilter.add(filmId);
    }

    // ==================== 电影详情缓存 ====================

    /**
     * 获取电影详情（带缓存保护）
     */
    public FilmVO getFilmDetail(Long filmId, Supplier<FilmVO> loader) {
        // 1. BloomFilter 检查（防止缓存穿透）
        if (!filmIdBloomFilter.contains(filmId)) {
            log.debug("BloomFilter 拦截，电影不存在: filmId={}", filmId);
            return null;
        }

        // 2. 检查空值缓存
        String nullKey = NULL_VALUE_KEY + filmId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            log.debug("命中空值缓存: filmId={}", filmId);
            return null;
        }

        // 3. 尝试从 L1（本地缓存）获取
        FilmVO localValue = filmDetailCache.getIfPresent(filmId);
        if (localValue != null) {
            log.debug("L1 缓存命中: filmId={}", filmId);
            return localValue;
        }

        // 4. 尝试从 L2（Redis）获取
        String redisKey = FILM_DETAIL_KEY + filmId;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            log.debug("L2 缓存命中: filmId={}", filmId);
            FilmVO filmVO = (FilmVO) redisValue;
            // 回填 L1
            filmDetailCache.put(filmId, filmVO);
            return filmVO;
        }

        // 5. 缓存未命中，使用互斥锁加载（防止缓存击穿）
        return loadWithMutex(filmId, redisKey, loader);
    }

    /**
     * 使用互斥锁加载数据
     */
    private FilmVO loadWithMutex(Long filmId, String redisKey, Supplier<FilmVO> loader) {
        String lockKey = "mutex:" + redisKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁（等待 3 秒，持有 10 秒）
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);

            if (acquired) {
                try {
                    // 双重检查
                    Object redisValue = redisTemplate.opsForValue().get(redisKey);
                    if (redisValue != null) {
                        FilmVO filmVO = (FilmVO) redisValue;
                        filmDetailCache.put(filmId, filmVO);
                        return filmVO;
                    }

                    // 从数据源加载
                    log.debug("从数据源加载电影详情: filmId={}", filmId);
                    FilmVO filmVO = loader.get();

                    if (filmVO != null) {
                        // 写入缓存（添加随机过期时间，防止雪崩）
                        long randomExpire = DETAIL_EXPIRE_SECONDS + (long) (Math.random() * 300);
                        redisTemplate.opsForValue().set(redisKey, filmVO, randomExpire, TimeUnit.SECONDS);
                        filmDetailCache.put(filmId, filmVO);
                    } else {
                        // 缓存空值
                        redisTemplate.opsForValue().set(NULL_VALUE_KEY + filmId, "NULL", 
                                NULL_EXPIRE_SECONDS, TimeUnit.SECONDS);
                    }

                    return filmVO;
                } finally {
                    lock.unlock();
                }
            } else {
                // 未获取到锁，等待后重试
                Thread.sleep(100);
                // 再次尝试从缓存获取
                Object redisValue = redisTemplate.opsForValue().get(redisKey);
                if (redisValue != null) {
                    return (FilmVO) redisValue;
                }
                // 降级：直接从数据源加载
                return loader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取互斥锁被中断: filmId={}", filmId);
            return loader.get();
        }
    }

    /**
     * 更新电影详情缓存
     */
    public void updateFilmDetail(Long filmId, FilmVO filmVO) {
        String redisKey = FILM_DETAIL_KEY + filmId;

        // 更新 L2
        long randomExpire = DETAIL_EXPIRE_SECONDS + (long) (Math.random() * 300);
        redisTemplate.opsForValue().set(redisKey, filmVO, randomExpire, TimeUnit.SECONDS);

        // 更新 L1
        filmDetailCache.put(filmId, filmVO);

        // 删除空值缓存
        redisTemplate.delete(NULL_VALUE_KEY + filmId);

        log.debug("更新电影详情缓存: filmId={}", filmId);
    }

    /**
     * 删除电影详情缓存
     */
    public void evictFilmDetail(Long filmId) {
        String redisKey = FILM_DETAIL_KEY + filmId;

        // 删除 L2
        redisTemplate.delete(redisKey);

        // 删除 L1
        filmDetailCache.invalidate(filmId);

        log.debug("删除电影详情缓存: filmId={}", filmId);
    }

    // ==================== 热门电影缓存 ====================

    /**
     * 获取热门电影列表
     */
    @SuppressWarnings("unchecked")
    public List<FilmVO> getHotFilms(String category, Supplier<List<FilmVO>> loader) {
        String cacheKey = category == null ? "all" : category;

        // 1. 尝试从 L1 获取
        List<FilmVO> localValue = hotFilmCache.getIfPresent(cacheKey);
        if (localValue != null) {
            log.debug("热门电影 L1 缓存命中: category={}", cacheKey);
            return localValue;
        }

        // 2. 尝试从 L2 获取
        String redisKey = FILM_HOT_KEY + cacheKey;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            log.debug("热门电影 L2 缓存命中: category={}", cacheKey);
            List<FilmVO> films = (List<FilmVO>) redisValue;
            hotFilmCache.put(cacheKey, films);
            return films;
        }

        // 3. 从数据源加载
        List<FilmVO> films = loader.get();
        if (films != null && !films.isEmpty()) {
            // 写入缓存
            long randomExpire = HOT_EXPIRE_SECONDS + (long) (Math.random() * 60);
            redisTemplate.opsForValue().set(redisKey, films, randomExpire, TimeUnit.SECONDS);
            hotFilmCache.put(cacheKey, films);
        }

        return films;
    }

    /**
     * 刷新热门电影缓存
     */
    public void refreshHotFilms() {
        // 清空本地缓存
        hotFilmCache.invalidateAll();

        // 删除 Redis 缓存
        var keys = redisTemplate.keys(FILM_HOT_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("热门电影缓存已刷新");
    }

    // ==================== 缓存统计 ====================

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        var detailStats = filmDetailCache.stats();
        var hotStats = hotFilmCache.stats();

        return Map.of(
                "filmDetail", Map.of(
                        "hitCount", detailStats.hitCount(),
                        "missCount", detailStats.missCount(),
                        "hitRate", String.format("%.2f%%", detailStats.hitRate() * 100),
                        "estimatedSize", filmDetailCache.estimatedSize()
                ),
                "hotFilm", Map.of(
                        "hitCount", hotStats.hitCount(),
                        "missCount", hotStats.missCount(),
                        "hitRate", String.format("%.2f%%", hotStats.hitRate() * 100),
                        "estimatedSize", hotFilmCache.estimatedSize()
                ),
                "bloomFilter", Map.of(
                        "expectedInsertions", filmIdBloomFilter.getExpectedInsertions(),
                        "count", filmIdBloomFilter.count()
                )
        );
    }
}
