package com.jelly.cinema.film.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.jelly.cinema.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentinel 降级处理器
 * 
 * 提供统一的熔断降级兜底逻辑
 * 
 * @author Jelly Cinema
 */
@Slf4j
public class SentinelFallbackHandler {

    /**
     * 电影详情降级处理
     */
    public static R<?> getFilmDetailFallback(Long id, BlockException ex) {
        log.warn("电影详情接口被限流/熔断, id={}, exception={}", id, ex.getClass().getSimpleName());
        return handleBlockException(ex, "电影详情服务暂时不可用");
    }

    /**
     * 电影详情异常降级
     */
    public static R<?> getFilmDetailExceptionFallback(Long id, Throwable ex) {
        log.error("电影详情接口异常, id={}", id, ex);
        return R.fail("获取电影详情失败，请稍后重试");
    }

    /**
     * 电影搜索降级处理
     */
    public static R<?> searchFilmFallback(String keyword, BlockException ex) {
        log.warn("电影搜索接口被限流/熔断, keyword={}, exception={}", keyword, ex.getClass().getSimpleName());
        return handleBlockException(ex, "搜索服务繁忙");
    }

    /**
     * 电影搜索异常降级
     */
    public static R<?> searchFilmExceptionFallback(String keyword, Throwable ex) {
        log.error("电影搜索接口异常, keyword={}", keyword, ex);
        return R.fail("搜索服务异常，请稍后重试");
    }

    /**
     * 电影推荐降级处理
     */
    public static R<?> recommendFilmFallback(Long userId, BlockException ex) {
        log.warn("电影推荐接口被限流/熔断, userId={}, exception={}", userId, ex.getClass().getSimpleName());
        return handleBlockException(ex, "推荐服务暂时不可用，请稍后查看");
    }

    /**
     * 电影推荐异常降级
     */
    public static R<?> recommendFilmExceptionFallback(Long userId, Throwable ex) {
        log.error("电影推荐接口异常, userId={}", userId, ex);
        return R.fail("推荐服务异常，请稍后重试");
    }

    /**
     * 热门电影降级处理
     */
    public static R<?> getHotFilmFallback(BlockException ex) {
        log.warn("热门电影接口被限流/熔断, exception={}", ex.getClass().getSimpleName());
        return handleBlockException(ex, "热门榜单加载中");
    }

    /**
     * 电影列表降级处理
     */
    public static R<?> listFilmFallback(Object query, BlockException ex) {
        log.warn("电影列表接口被限流/熔断, exception={}", ex.getClass().getSimpleName());
        return handleBlockException(ex, "电影列表加载中");
    }

    /**
     * ES 搜索降级处理
     */
    public static R<?> esSearchFallback(String keyword, BlockException ex) {
        log.warn("ES 搜索被限流/熔断, keyword={}, exception={}", keyword, ex.getClass().getSimpleName());
        return handleBlockException(ex, "搜索服务正在恢复中");
    }

    /**
     * ES 搜索异常降级
     */
    public static R<?> esSearchExceptionFallback(String keyword, Throwable ex) {
        log.error("ES 搜索异常, keyword={}", keyword, ex);
        return R.fail("搜索服务暂时不可用");
    }

    /**
     * 通用的 BlockException 处理
     */
    private static R<?> handleBlockException(BlockException ex, String defaultMsg) {
        String message;
        int code;

        if (ex instanceof FlowException) {
            // 限流异常
            message = "请求过于频繁，请稍后再试";
            code = 429;
        } else if (ex instanceof DegradeException) {
            // 熔断异常
            message = defaultMsg + "（服务熔断中）";
            code = 503;
        } else {
            // 其他 Sentinel 异常
            message = defaultMsg;
            code = 503;
        }

        return R.fail(code, message);
    }

    /**
     * 通用限流降级处理
     */
    public static R<?> defaultBlockHandler(BlockException ex) {
        log.warn("触发默认限流降级处理, exception={}", ex.getClass().getSimpleName());
        return handleBlockException(ex, "服务暂时不可用");
    }

    /**
     * 通用异常降级处理
     */
    public static R<?> defaultFallback(Throwable ex) {
        log.error("触发默认异常降级处理", ex);
        return R.fail("系统繁忙，请稍后重试");
    }
}
