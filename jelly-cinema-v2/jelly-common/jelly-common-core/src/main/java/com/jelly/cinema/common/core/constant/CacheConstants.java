package com.jelly.cinema.common.core.constant;

/**
 * 缓存常量
 *
 * @author Jelly Cinema
 */
public interface CacheConstants {

    /**
     * 缓存前缀
     */
    String CACHE_PREFIX = "jelly:";

    /**
     * 登录用户 Token 前缀
     */
    String LOGIN_TOKEN_KEY = CACHE_PREFIX + "login:token:";

    /**
     * 用户信息缓存前缀
     */
    String USER_INFO_KEY = CACHE_PREFIX + "user:info:";

    /**
     * 验证码前缀
     */
    String CAPTCHA_CODE_KEY = CACHE_PREFIX + "captcha:";

    /**
     * 电影信息缓存前缀
     */
    String FILM_INFO_KEY = CACHE_PREFIX + "film:info:";

    /**
     * 电影热门榜单
     */
    String FILM_HOT_RANK_KEY = CACHE_PREFIX + "film:hot:rank";

    /**
     * 用户在线状态前缀
     */
    String USER_ONLINE_KEY = CACHE_PREFIX + "user:online:";

    /**
     * IM 消息队列前缀
     */
    String IM_MSG_QUEUE_KEY = CACHE_PREFIX + "im:msg:queue:";

    /**
     * 默认过期时间（秒）- 30分钟
     */
    long EXPIRE_TIME = 1800;

    /**
     * Token 过期时间（秒）- 24小时
     */
    long TOKEN_EXPIRE_TIME = 86400;
}
