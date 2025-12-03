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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        
        log.debug("Gateway filter path: {}", path);

        try {
            // 如果已登录，将用户信息添加到请求头
            if (StpUtil.isLogin()) {
                Object loginId = StpUtil.getLoginId();
                ServerHttpRequest.Builder mutate = request.mutate();
                
                // 设置用户 ID
                if (loginId != null) {
                    mutate.header(HEADER_USER_ID, String.valueOf(loginId));
                }
                
                // 从 Session 中获取用户名（如果存在）
                try {
                    Object username = StpUtil.getSession().get("username");
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

    @Override
    public int getOrder() {
        return -100;
    }
}
