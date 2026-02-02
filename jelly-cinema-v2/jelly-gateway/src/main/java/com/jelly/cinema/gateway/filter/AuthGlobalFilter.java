package com.jelly.cinema.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局认证过滤器
 * 将用户信息传递给下游服务
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_AUTHORIZATION = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        log.debug("Gateway filter path: {}", path);

        try {
            Object loginId = null;
            
            // 首先尝试 Sa-Token 的标准登录检查（基于 Cookie/Header）
            if (StpUtil.isLogin()) {
                loginId = StpUtil.getLoginId();
            } else {
                String token = resolveToken(request);
                if (StrUtil.isNotBlank(token)) {
                    loginId = StpUtil.getLoginIdByToken(token);
                    log.debug("Token auth: token={}, loginId={}",
                            token.substring(0, Math.min(8, token.length())) + "...", loginId);
                }
            }
            
            // 如果获取到 loginId，添加到请求头
            if (loginId != null) {
                ServerHttpRequest.Builder mutate = request.mutate();
                mutate.header(HEADER_USER_ID, String.valueOf(loginId));
                
                // 尝试获取用户名
                try {
                    Object username = StpUtil.getSessionByLoginId(loginId).get("username");
                    if (username != null) {
                        mutate.header(HEADER_USERNAME, String.valueOf(username));
                    }
                } catch (Exception e) {
                    log.debug("Cannot get username from session: {}", e.getMessage());
                }

                return chain.filter(exchange.mutate().request(mutate.build()).build());
            }
        } catch (Exception e) {
            log.debug("Auth filter error: {}", e.getMessage());
            // 如果出错，继续处理请求（可能是白名单路径）
        }

        return chain.filter(exchange);
    }

    private String resolveToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (StrUtil.isBlank(token)) {
            token = request.getQueryParams().getFirst("token");
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
    public int getOrder() {
        return -100;
    }
}

