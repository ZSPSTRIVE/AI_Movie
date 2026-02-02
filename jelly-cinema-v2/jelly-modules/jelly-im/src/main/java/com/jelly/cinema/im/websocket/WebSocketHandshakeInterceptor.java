package com.jelly.cinema.im.websocket;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 
 * 注意：WebSocket 请求通过 Gateway 时，Gateway 会：
 * 1. 从 URL query 参数中获取 token
 * 2. 验证 token 并将用户信息添加到请求头 X-User-Id
 * 
 * 所以这里优先从请求头读取用户信息，回退到直接验证 token
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final String USER_ID_KEY = "userId";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    private final Environment environment;

    public WebSocketHandshakeInterceptor(Environment environment) {
        this.environment = environment;
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        log.info("WebSocket 握手请求: URI={}, RemoteAddress={}", request.getURI(), request.getRemoteAddress());
        
        if (request instanceof ServletServerHttpRequest servletRequest) {
            // 方式1：从 Gateway 传递的请求头获取用户 ID（推荐）
            String userIdHeader = servletRequest.getServletRequest().getHeader(HEADER_USER_ID);
            log.info("WebSocket X-User-Id 头: {}", userIdHeader);
            
            if (StrUtil.isNotBlank(userIdHeader)) {
                try {
                    Long userId = Long.valueOf(userIdHeader);
                    attributes.put(USER_ID_KEY, userId);
                    log.info("WebSocket 握手成功 (via Gateway), userId: {}", userId);
                    return true;
                } catch (NumberFormatException e) {
                    log.warn("WebSocket X-User-Id 格式错误: {}", userIdHeader);
                }
            }
            
            // 方式2：回退到直接验证 token（用于测试或直连场景）
            String token = resolveToken(servletRequest);
            log.info("WebSocket token: {}", token != null ? token.substring(0, Math.min(8, token.length())) + "..." : "null");
            
            Long userId = null;

            if (StrUtil.isNotBlank(token)) {
                try {
                    // Validate token
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    log.info("WebSocket token validation: loginId={}", loginId);

                    if (loginId != null) {
                        userId = Long.valueOf(loginId.toString());
                    }
                } catch (Exception e) {
                    log.error("WebSocket token validation error", e);
                }
            }

            // Dev fallback: allow passing userId directly for local testing
            if (userId == null && isDevProfile()) {
                String userIdParam = servletRequest.getServletRequest().getParameter("userId");
                if (StrUtil.isNotBlank(userIdParam)) {
                    try {
                        userId = Long.valueOf(userIdParam);
                        attributes.put(USER_ID_KEY, userId);
                        log.warn("WebSocket handshake OK (dev fallback), userId: {}", userId);
                        return true;
                    } catch (NumberFormatException e) {
                        log.warn("WebSocket userId param invalid: {}", userIdParam);
                    }
                }
            }

            if (userId == null) {
                log.warn("WebSocket connect failed: missing auth or invalid token");
                return false;
            }

            attributes.put(USER_ID_KEY, userId);
            log.info("WebSocket handshake OK (via token), userId: {}", userId);
            return true;
        }
        log.warn("WebSocket 握手失败: 不是 ServletServerHttpRequest, 实际类型={}", request.getClass().getName());
        return false;
    }

    private String resolveToken(ServletServerHttpRequest servletRequest) {
        String token = servletRequest.getServletRequest().getHeader(HEADER_AUTHORIZATION);
        if (StrUtil.isBlank(token)) {
            token = servletRequest.getServletRequest().getParameter("token");
        }
        if (StrUtil.isBlank(token)) {
            return null;
        }
        if (StrUtil.startWithIgnoreCase(token, "Bearer ")) {
            return token.substring(7).trim();
        }
        return token.trim();
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的处理
    }
}

