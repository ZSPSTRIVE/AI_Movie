package com.jelly.cinema.common.redis.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * Redis Key 事件监听器
 * 
 * 监听 Redis Keyspace Notifications：
 * - Key 过期事件
 * - Key 删除事件
 * - Key 更新事件
 * 
 * 用于保持多实例间的本地缓存一致性
 * 
 * 注意：需要在 Redis 中开启 Keyspace Notifications：
 * CONFIG SET notify-keyspace-events "Ex"  # 监听过期事件
 * CONFIG SET notify-keyspace-events "Kg"  # 监听通用命令和过期
 * CONFIG SET notify-keyspace-events "AKE" # 监听所有事件
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheKeyEventListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final MultiLevelCache multiLevelCache;

    /**
     * 缓存 Key 前缀
     */
    private static final String CACHE_KEY_PREFIX = "cache:";

    @PostConstruct
    public void init() {
        log.info("===== 初始化 Redis Key 事件监听器 =====");

        // 监听 Key 过期事件
        listenerContainer.addMessageListener(
                new KeyExpiredListener(),
                new PatternTopic("__keyevent@*__:expired")
        );

        // 监听 Key 删除事件
        listenerContainer.addMessageListener(
                new KeyDeletedListener(),
                new PatternTopic("__keyevent@*__:del")
        );

        // 监听 Key 设置事件（可选，用于感知其他实例的缓存更新）
        listenerContainer.addMessageListener(
                new KeySetListener(),
                new PatternTopic("__keyevent@*__:set")
        );

        log.info("===== Redis Key 事件监听器初始化完成 =====");
    }

    /**
     * Key 过期事件监听器
     */
    private class KeyExpiredListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            handleKeyEvent(key, "expired");
        }
    }

    /**
     * Key 删除事件监听器
     */
    private class KeyDeletedListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            handleKeyEvent(key, "deleted");
        }
    }

    /**
     * Key 设置事件监听器
     */
    private class KeySetListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String key = new String(message.getBody(), StandardCharsets.UTF_8);
            handleKeyEvent(key, "set");
        }
    }

    /**
     * 处理 Key 事件
     */
    private void handleKeyEvent(String key, String eventType) {
        // 只处理缓存相关的 Key
        if (!key.startsWith(CACHE_KEY_PREFIX)) {
            return;
        }

        log.debug("收到 Redis Key 事件: key={}, event={}", key, eventType);

        try {
            // 解析缓存名称和 Key
            // 格式：cache:{cacheName}:{key}
            String[] parts = key.substring(CACHE_KEY_PREFIX.length()).split(":", 2);
            if (parts.length < 2) {
                return;
            }

            String cacheName = parts[0];
            String cacheKey = parts[1];

            switch (eventType) {
                case "expired":
                case "deleted":
                    // 删除本地缓存
                    multiLevelCache.evictLocal(cacheName, cacheKey);
                    log.debug("本地缓存已清除: cacheName={}, key={}", cacheName, cacheKey);
                    break;

                case "set":
                    // 刷新本地缓存（从 Redis 重新加载）
                    multiLevelCache.refreshLocal(cacheName, cacheKey);
                    log.debug("本地缓存已刷新: cacheName={}, key={}", cacheName, cacheKey);
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.error("处理 Redis Key 事件失败: key={}, event={}", key, eventType, e);
        }
    }
}

/**
 * Key 过期事件监听器（Spring Data Redis 内置）
 */
@Slf4j
@Component
class CacheKeyExpirationListener extends KeyExpirationEventMessageListener {

    private final MultiLevelCache multiLevelCache;

    public CacheKeyExpirationListener(RedisMessageListenerContainer listenerContainer,
                                       MultiLevelCache multiLevelCache) {
        super(listenerContainer);
        this.multiLevelCache = multiLevelCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        // 只处理缓存相关的 Key
        if (!expiredKey.startsWith("cache:")) {
            return;
        }

        log.debug("收到 Key 过期事件: key={}", expiredKey);

        try {
            // 解析缓存名称和 Key
            String[] parts = expiredKey.substring("cache:".length()).split(":", 2);
            if (parts.length >= 2) {
                String cacheName = parts[0];
                String cacheKey = parts[1];
                multiLevelCache.evictLocal(cacheName, cacheKey);
            }
        } catch (Exception e) {
            log.error("处理 Key 过期事件失败: key={}", expiredKey, e);
        }
    }
}
