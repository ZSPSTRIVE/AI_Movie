package com.jelly.cinema.common.redis.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存保护机制
 * 
 * 1. 缓存穿透保护：BloomFilter 拦截非法请求
 * 2. 缓存击穿保护：互斥锁防止热点 Key 并发重建
 * 3. 缓存雪崩保护：随机过期时间 + 多级缓存兜底
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheProtection {

    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MultiLevelCache multiLevelCache;

    /**
     * BloomFilter 名称前缀
     */
    private static final String BLOOM_FILTER_PREFIX = "bloom:";

    /**
     * 互斥锁前缀
     */
    private static final String MUTEX_LOCK_PREFIX = "mutex:";

    /**
     * 空值缓存前缀
     */
    private static final String NULL_VALUE_PREFIX = "null:";

    /**
     * 空值缓存过期时间（秒）
     */
    private static final long NULL_VALUE_EXPIRE = 60;

    /**
     * 互斥锁等待时间（秒）
     */
    private static final long MUTEX_WAIT_TIME = 3;

    /**
     * 互斥锁持有时间（秒）
     */
    private static final long MUTEX_LEASE_TIME = 10;

    // ==================== BloomFilter 相关方法 ====================

    /**
     * 初始化 BloomFilter
     * 
     * @param filterName 过滤器名称
     * @param expectedInsertions 预期插入数量
     * @param falseProbability 误判率（建议 0.01）
     */
    public void initBloomFilter(String filterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + filterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        log.info("初始化 BloomFilter: name={}, expectedInsertions={}, falseProbability={}", 
                filterName, expectedInsertions, falseProbability);
    }

    /**
     * 向 BloomFilter 添加元素
     */
    public void addToBloomFilter(String filterName, String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + filterName);
        bloomFilter.add(value);
    }

    /**
     * 批量添加到 BloomFilter
     */
    public void addAllToBloomFilter(String filterName, Iterable<String> values) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + filterName);
        for (String value : values) {
            bloomFilter.add(value);
        }
    }

    /**
     * 检查元素是否可能存在
     * 
     * @return true 表示可能存在，false 表示一定不存在
     */
    public boolean mightContain(String filterName, String value) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_PREFIX + filterName);
        return bloomFilter.contains(value);
    }

    /**
     * 使用 BloomFilter 保护的缓存查询
     * 
     * 先通过 BloomFilter 检查，如果不存在则直接返回 null，避免缓存穿透
     */
    public <T> T getWithBloomFilter(String filterName, String cacheName, String key, 
                                     Supplier<T> loader, long redisExpireSeconds) {
        // 1. BloomFilter 检查
        if (!mightContain(filterName, key)) {
            log.debug("BloomFilter 拦截，key 不存在: filterName={}, key={}", filterName, key);
            return null;
        }

        // 2. 正常缓存查询
        return multiLevelCache.get(cacheName, key, loader, redisExpireSeconds);
    }

    // ==================== 缓存击穿保护（互斥锁）====================

    /**
     * 带互斥锁保护的缓存查询
     * 
     * 当缓存失效时，只允许一个线程去重建缓存，其他线程等待
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithMutex(String cacheName, String key, Supplier<T> loader, long redisExpireSeconds) {
        String fullKey = "cache:" + cacheName + ":" + key;

        // 1. 先尝试从缓存获取
        T value = multiLevelCache.get(cacheName, key);
        if (value != null) {
            return value;
        }

        // 2. 检查是否是空值缓存
        String nullKey = NULL_VALUE_PREFIX + fullKey;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(nullKey))) {
            log.debug("命中空值缓存: key={}", fullKey);
            return null;
        }

        // 3. 缓存未命中，尝试获取互斥锁
        String lockKey = MUTEX_LOCK_PREFIX + fullKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁（等待 3 秒，持有 10 秒）
            boolean acquired = lock.tryLock(MUTEX_WAIT_TIME, MUTEX_LEASE_TIME, TimeUnit.SECONDS);
            
            if (acquired) {
                try {
                    // 双重检查：获取锁后再次检查缓存
                    value = multiLevelCache.get(cacheName, key);
                    if (value != null) {
                        return value;
                    }

                    // 从数据源加载
                    log.debug("获取互斥锁，开始重建缓存: key={}", fullKey);
                    value = loader.get();

                    if (value != null) {
                        // 写入缓存
                        multiLevelCache.put(cacheName, key, value, redisExpireSeconds);
                    } else {
                        // 缓存空值，防止缓存穿透
                        cacheNullValue(nullKey);
                    }

                    return value;
                } finally {
                    lock.unlock();
                }
            } else {
                // 未获取到锁，等待一段时间后重试
                log.debug("未获取到互斥锁，等待后重试: key={}", fullKey);
                Thread.sleep(100);
                return getWithMutex(cacheName, key, loader, redisExpireSeconds);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取互斥锁被中断: key={}", fullKey, e);
            // 降级：直接从数据源加载
            return loader.get();
        }
    }

    /**
     * 缓存空值
     */
    private void cacheNullValue(String nullKey) {
        redisTemplate.opsForValue().set(nullKey, "NULL", NULL_VALUE_EXPIRE, TimeUnit.SECONDS);
        log.debug("缓存空值: key={}, expire={}s", nullKey, NULL_VALUE_EXPIRE);
    }

    // ==================== 逻辑过期方案（热点 Key 保护）====================

    /**
     * 带逻辑过期的缓存数据
     */
    public static class CacheData<T> {
        private T data;
        private long expireTime; // 逻辑过期时间戳（毫秒）

        public CacheData(T data, long expireSeconds) {
            this.data = data;
            this.expireTime = System.currentTimeMillis() + expireSeconds * 1000;
        }

        public T getData() { return data; }
        public boolean isExpired() { return System.currentTimeMillis() > expireTime; }
    }

    /**
     * 使用逻辑过期的缓存查询
     * 
     * 数据永不过期，但包含逻辑过期时间。
     * 查询时如果逻辑过期，异步重建缓存，同时返回旧数据。
     * 适用于热点 Key，保证高可用。
     */
    @SuppressWarnings("unchecked")
    public <T> T getWithLogicalExpire(String cacheName, String key, Supplier<T> loader, 
                                       long logicalExpireSeconds) {
        String fullKey = "cache:" + cacheName + ":" + key;

        // 1. 从缓存获取
        Object cached = redisTemplate.opsForValue().get(fullKey);
        if (cached == null) {
            // 缓存不存在，直接加载
            T value = loader.get();
            if (value != null) {
                setWithLogicalExpire(cacheName, key, value, logicalExpireSeconds);
            }
            return value;
        }

        CacheData<T> cacheData = (CacheData<T>) cached;

        // 2. 检查逻辑过期
        if (!cacheData.isExpired()) {
            // 未过期，直接返回
            return cacheData.getData();
        }

        // 3. 已过期，尝试异步重建
        String lockKey = MUTEX_LOCK_PREFIX + fullKey;
        RLock lock = redissonClient.getLock(lockKey);

        // 尝试获取锁（非阻塞）
        if (lock.tryLock()) {
            try {
                // 异步重建缓存
                Thread.startVirtualThread(() -> {
                    try {
                        T newValue = loader.get();
                        if (newValue != null) {
                            setWithLogicalExpire(cacheName, key, newValue, logicalExpireSeconds);
                        }
                    } finally {
                        lock.unlock();
                    }
                });
            } catch (Exception e) {
                lock.unlock();
                log.error("异步重建缓存失败: key={}", fullKey, e);
            }
        }

        // 4. 返回旧数据
        return cacheData.getData();
    }

    /**
     * 设置带逻辑过期的缓存
     */
    public <T> void setWithLogicalExpire(String cacheName, String key, T value, long logicalExpireSeconds) {
        String fullKey = "cache:" + cacheName + ":" + key;
        CacheData<T> cacheData = new CacheData<>(value, logicalExpireSeconds);
        // Redis 中永不过期，由逻辑过期时间控制
        redisTemplate.opsForValue().set(fullKey, cacheData);
        log.debug("设置逻辑过期缓存: key={}, logicalExpire={}s", fullKey, logicalExpireSeconds);
    }

    // ==================== 缓存预热 ====================

    /**
     * 预热缓存
     */
    public <T> void warmUp(String cacheName, String key, Supplier<T> loader, long redisExpireSeconds) {
        T value = loader.get();
        if (value != null) {
            multiLevelCache.put(cacheName, key, value, redisExpireSeconds);
            log.info("预热缓存: cacheName={}, key={}", cacheName, key);
        }
    }
}
