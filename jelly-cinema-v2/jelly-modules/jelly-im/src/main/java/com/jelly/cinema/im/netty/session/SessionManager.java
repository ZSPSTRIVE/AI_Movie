package com.jelly.cinema.im.netty.session;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器
 * 
 * 管理用户连接会话：
 * 1. 本地内存：Channel 映射
 * 2. Redis：分布式在线状态
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "netty.server.enabled", havingValue = "true", matchIfMissing = false)
public class SessionManager {

    /**
     * Channel 属性 Key：用户 ID
     */
    public static final AttributeKey<Long> USER_ID_KEY = AttributeKey.valueOf("userId");

    /**
     * Channel 属性 Key：最后心跳时间
     */
    public static final AttributeKey<Long> LAST_HEARTBEAT_KEY = AttributeKey.valueOf("lastHeartbeat");

    /**
     * 本地会话映射：userId -> Channel
     */
    private static final Map<Long, Channel> LOCAL_USER_CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * 本地会话映射：channelId -> userId
     */
    private static final Map<String, Long> LOCAL_CHANNEL_USER_MAP = new ConcurrentHashMap<>();

    /**
     * Redis Key 前缀
     */
    private static final String ONLINE_USER_KEY = "im:online:user:";
    private static final String USER_SERVER_KEY = "im:user:server:";

    /**
     * 在线状态过期时间（秒）
     */
    private static final long ONLINE_EXPIRE_SECONDS = 300;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 当前服务器地址（用于分布式场景）
     */
    private String serverAddress;

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    /**
     * 绑定用户与 Channel
     */
    public void bind(Long userId, Channel channel) {
        // 1. 移除旧连接（如果存在）
        Channel oldChannel = LOCAL_USER_CHANNEL_MAP.get(userId);
        if (oldChannel != null && oldChannel != channel) {
            log.info("用户 {} 的旧连接将被关闭", userId);
            String oldChannelId = oldChannel.id().asLongText();
            LOCAL_CHANNEL_USER_MAP.remove(oldChannelId);
            oldChannel.close();
        }

        // 2. 绑定新连接
        String channelId = channel.id().asLongText();
        channel.attr(USER_ID_KEY).set(userId);
        channel.attr(LAST_HEARTBEAT_KEY).set(System.currentTimeMillis());

        LOCAL_USER_CHANNEL_MAP.put(userId, channel);
        LOCAL_CHANNEL_USER_MAP.put(channelId, userId);

        // 3. 更新 Redis 在线状态
        updateOnlineStatus(userId, true);

        log.info("用户 {} 绑定成功, channelId={}", userId, channelId);
    }

    /**
     * 解绑用户与 Channel
     */
    public void unbind(Channel channel) {
        String channelId = channel.id().asLongText();
        Long userId = LOCAL_CHANNEL_USER_MAP.remove(channelId);

        if (userId != null) {
            LOCAL_USER_CHANNEL_MAP.remove(userId);
            // 更新 Redis 离线状态
            updateOnlineStatus(userId, false);
            log.info("用户 {} 解绑成功, channelId={}", userId, channelId);
        }
    }

    /**
     * 获取用户的 Channel
     */
    public Channel getChannel(Long userId) {
        return LOCAL_USER_CHANNEL_MAP.get(userId);
    }

    /**
     * 获取 Channel 对应的用户 ID
     */
    public Long getUserId(Channel channel) {
        return channel.attr(USER_ID_KEY).get();
    }

    /**
     * 判断用户是否在线（本地）
     */
    public boolean isOnlineLocal(Long userId) {
        Channel channel = LOCAL_USER_CHANNEL_MAP.get(userId);
        return channel != null && channel.isActive();
    }

    /**
     * 判断用户是否在线（全局）
     */
    public boolean isOnlineGlobal(Long userId) {
        String key = ONLINE_USER_KEY + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取用户所在服务器地址
     */
    public String getUserServerAddress(Long userId) {
        String key = USER_SERVER_KEY + userId;
        Object address = redisTemplate.opsForValue().get(key);
        return address != null ? address.toString() : null;
    }

    /**
     * 更新在线状态到 Redis
     */
    private void updateOnlineStatus(Long userId, boolean online) {
        try {
            String onlineKey = ONLINE_USER_KEY + userId;
            String serverKey = USER_SERVER_KEY + userId;

            if (online) {
                // 设置在线状态
                redisTemplate.opsForValue().set(onlineKey, System.currentTimeMillis(), 
                        ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                // 设置用户所在服务器
                if (serverAddress != null) {
                    redisTemplate.opsForValue().set(serverKey, serverAddress, 
                            ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
                }
            } else {
                // 删除在线状态
                redisTemplate.delete(onlineKey);
                redisTemplate.delete(serverKey);
            }
        } catch (Exception e) {
            log.error("更新 Redis 在线状态失败: userId={}, online={}", userId, online, e);
        }
    }

    /**
     * 刷新在线状态（心跳时调用）
     */
    public void refreshOnlineStatus(Long userId) {
        try {
            String onlineKey = ONLINE_USER_KEY + userId;
            String serverKey = USER_SERVER_KEY + userId;

            redisTemplate.expire(onlineKey, ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            if (serverAddress != null) {
                redisTemplate.expire(serverKey, ONLINE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("刷新在线状态失败: userId={}", userId, e);
        }
    }

    /**
     * 更新最后心跳时间
     */
    public void updateLastHeartbeat(Channel channel) {
        channel.attr(LAST_HEARTBEAT_KEY).set(System.currentTimeMillis());
        Long userId = getUserId(channel);
        if (userId != null) {
            refreshOnlineStatus(userId);
        }
    }

    /**
     * 获取最后心跳时间
     */
    public Long getLastHeartbeat(Channel channel) {
        return channel.attr(LAST_HEARTBEAT_KEY).get();
    }

    /**
     * 获取本地在线用户数
     */
    public int getLocalOnlineCount() {
        return LOCAL_USER_CHANNEL_MAP.size();
    }

    /**
     * 获取所有本地在线用户 ID
     */
    public Set<Long> getLocalOnlineUserIds() {
        return LOCAL_USER_CHANNEL_MAP.keySet();
    }

    /**
     * 向用户发送消息
     */
    public boolean sendToUser(Long userId, Object message) {
        Channel channel = getChannel(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
            return true;
        }
        return false;
    }

    /**
     * 广播消息给所有在线用户
     */
    public void broadcast(Object message) {
        LOCAL_USER_CHANNEL_MAP.values().forEach(channel -> {
            if (channel.isActive()) {
                channel.writeAndFlush(message);
            }
        });
    }
}
