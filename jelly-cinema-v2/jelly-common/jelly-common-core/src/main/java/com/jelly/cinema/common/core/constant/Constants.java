package com.jelly.cinema.common.core.constant;

/**
 * 通用常量
 *
 * @author Jelly Cinema
 */
public interface Constants {

    /**
     * UTF-8 编码
     */
    String UTF8 = "UTF-8";

    /**
     * Token 前缀
     */
    String TOKEN_PREFIX = "Bearer ";

    /**
     * Token 头名称
     */
    String TOKEN_HEADER = "Authorization";

    /**
     * 用户 ID 请求头
     */
    String HEADER_USER_ID = "X-User-Id";

    /**
     * 用户名请求头
     */
    String HEADER_USERNAME = "X-Username";

    /**
     * 成功标记
     */
    Integer SUCCESS = 200;

    /**
     * 失败标记
     */
    Integer FAIL = 500;

    /**
     * 未授权
     */
    Integer UNAUTHORIZED = 401;

    /**
     * 禁止访问
     */
    Integer FORBIDDEN = 403;
}
