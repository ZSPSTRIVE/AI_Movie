package com.jelly.cinema.common.redis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存实现
 * 
 * L1: Caffeine 本地缓存（高性能，容量有限）
 * L2: Redis 分布式缓存（支持集群，容量大）
 * 
 * 读取顺序：L1 -> L2 -> DB
 * 写入顺序：DB -> L2 -> L1
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class MultiLevelCache {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 本地缓存容器（支持多个不同配置的缓存实例）
     */
    private final Map<String, Cache<String, Object>> localCaches = new ConcurrentHashMap<>();

    /**
     * 默认本地缓存配置
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 100;
    private static final int DEFAULT_MAXIMUM_SIZE = 10000;
    private static final Duration DEFAULT_EXPIRE_AFTER_WRITE = Duration.ofMinutes(5);

    /**
     * Redis 默认过期时间（秒）
     */
    private static final long DEFAULT_REDIS_EXPIRE_SECONDS = 3600;

    public MultiLevelCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 初始化默认缓存
        initDefaultCaches();
    }

    /**
     * 初始化默认缓存实例
     */
    private void initDefaultCaches() {
        // 电影信息缓存（容量大，过期时间长）
        createCache("film", 200, 50000, Duration.ofMinutes(30));
        
        // 热门数据缓存（容量中等，过期时间短）
        createCache("hot", 100, 5000, Duration.ofMinutes(5));
        
        // 用户信息缓存
        createCache("user", 100, 10000, Duration.ofMinutes(10));
        
        // 搜索结果缓存
        createCache("search", 50, 1000, Duration.ofMinutes(2));
        
        // 推荐结果缓存
        createCache("recommend", 50, 2000, Duration.ofMinutes(5));
    }

    /**
     * 创建缓存实例
     */
    public void createCache(String cacheName, int initialCapacity, int maximumSize, Duration expireAfterWrite) {
        Cache<String, Object> cache = Caffeine.newBuilder()
                .initialCapacity(initialCapacity)
                .maximumSize(maximumSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats() // 开启统计
                .build();
        localCaches.put(cacheName, cache);
        log.info("创建本地缓存: name={}, maxSize={}, expireAfterWrite={}", 
                cacheName, maximumSize, expireAfterWrite);
    }

    /**
     * 获取缓存（带自动加载）
     * 
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @param loader 数据加载器（缓存未命中时调用）
     * @param redisExpireSeconds Redis 过期时间（秒）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Supplier<T> loader, long redisExpireSeconds) {
        String fullKey = buildKey(cacheName, key);

        // 1. 尝试从 L1（本地缓存）获取
        Cache<String, Object> localCache = getOrCreateCache(cacheName);
        Object localValue = localCache.getIfPresent(fullKey);
        if (localValue != null) {
            log.debug("L1 缓存命中: key={}", fullKey);
            return (T) localValue;
        }

        // 2. 尝试从 L2（Redis）获取
        Object redisValue = redisTemplate.opsForValue().get(fullKey);
        if (redisValue != null) {
            log.debug("L2 缓存命中: key={}", fullKey);
            // 回填 L1
            localCache.put(fullKey, redisValue);
            return (T) redisValue;
        }

        // 3. 缓存未命中，从数据源加载
        if (loader != null) {
            T value = loader.get();
            if (value != null) {
                log.debug("缓存未命中，从数据源加载: key={}", fullKey);
                // 写入 L2 和 L1
                put(cacheName, key, value, redisExpireSeconds);
            }
            return value;
        }

        return null;
    }

    /**
     * 获取缓存（使用默认过期时间）
     */
    public <T> T get(String cacheName, String key, Supplier<T> loader) {
        return get(cacheName, key, loader, DEFAULT_REDIS_EXPIRE_SECONDS);
    }

    /**
     * 直接获取缓存（不自动加载）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        return get(cacheName, key, null, 0);
    }

    /**
     * 写入缓存
     */
    public void put(String cacheName, String key, Object value, long redisExpireSeconds) {
        String fullKey = buildKey(cacheName, key);

        // 写入 L2（Redis）
        if (redisExpireSeconds > 0) {
            // 添加随机过期时间，防止缓存雪崩
            long randomExpire = redisExpireSeconds + (long) (Math.random() * 60);
            redisTemplate.opsForValue().set(fullKey, value, randomExpire, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(fullKey, value);
        }

        // 写入 L1（本地缓存）
        Cache<String, Object> localCache = getOrCreateCache(cacheName);
        localCache.put(fullKey, value);

        log.debug("写入缓存: key={}, redisExpire={}s", fullKey, redisExpireSeconds);
    }

    /**
     * 写入缓存（使用默认过期时间）
     */
    public void put(String cacheName, String key, Object value) {
        put(cacheName, key, value, DEFAULT_REDIS_EXPIRE_SECONDS);
    }

    /**
     * 删除缓存
     */
    public void evict(String cacheName, String key) {
        String fullKey = buildKey(cacheName, key);

        // 删除 L2
        redisTemplate.delete(fullKey);

        // 删除 L1
        Cache<String, Object> localCache = localCaches.get(cacheName);
        if (localCache != null) {
            localCache.invalidate(fullKey);
        }

        log.debug("删除缓存: key={}", fullKey);
    }

    /**
     * 清空指定缓存
     */
    public void clear(String cacheName) {
        // 清空 L1
        Cache<String, Object> localCache = localCaches.get(cacheName);
        if (localCache != null) {
            localCache.invalidateAll();
        }

        // 清空 L2（按前缀删除）
        String pattern = "cache:" + cacheName + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("清空缓存: cacheName={}", cacheName);
    }

    /**
     * 仅删除本地缓存（用于接收 Redis Key 事件时同步）
     */
    public void evictLocal(String cacheName, String key) {
        String fullKey = buildKey(cacheName, key);
        Cache<String, Object> localCache = localCaches.get(cacheName);
        if (localCache != null) {
            localCache.invalidate(fullKey);
            log.debug("删除本地缓存: key={}", fullKey);
        }
    }

    /**
     * 刷新本地缓存（从 Redis 重新加载）
     */
    @SuppressWarnings("unchecked")
    public <T> T refreshLocal(String cacheName, String key) {
        String fullKey = buildKey(cacheName, key);

        // 从 Redis 获取最新值
        Object redisValue = redisTemplate.opsForValue().get(fullKey);
        if (redisValue != null) {
            // 更新本地缓存
            Cache<String, Object> localCache = getOrCreateCache(cacheName);
            localCache.put(fullKey, redisValue);
            log.debug("刷新本地缓存: key={}", fullKey);
            return (T) redisValue;
        }

        return null;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getStats(String cacheName) {
        Cache<String, Object> cache = localCaches.get(cacheName);
        if (cache == null) {
            return Map.of();
        }

        var stats = cache.stats();
        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", stats.hitRate(),
                "evictionCount", stats.evictionCount(),
                "estimatedSize", cache.estimatedSize()
        );
    }

    /**
     * 构建完整的缓存 Key
     */
    private String buildKey(String cacheName, String key) {
        return "cache:" + cacheName + ":" + key;
    }

    /**
     * 获取或创建缓存实例
     */
    private Cache<String, Object> getOrCreateCache(String cacheName) {
        return localCaches.computeIfAbsent(cacheName, name -> 
                Caffeine.newBuilder()
                        .initialCapacity(DEFAULT_INITIAL_CAPACITY)
                        .maximumSize(DEFAULT_MAXIMUM_SIZE)
                        .expireAfterWrite(DEFAULT_EXPIRE_AFTER_WRITE)
                        .recordStats()
                        .build()
        );
    }
}
