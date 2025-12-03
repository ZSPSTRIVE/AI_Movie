package com.jelly.cinema.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 管理员权限过滤器
 *
 * @author Jelly Cinema
 */
@Slf4j
@Component
public class AdminAuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // 临时禁用管理员权限校验，后续再完善
        if (false && path.startsWith("/admin")) {
            try {
                // 检查是否登录
                if (!StpUtil.isLogin()) {
                    return unauthorized(exchange, "请先登录");
                }
                
                // 获取用户角色
                Object loginId = StpUtil.getLoginId();
                // 从 Session 中获取角色（LoginHelper 登录时会单独存储 role）
                String role = (String) StpUtil.getSession().get("role");
                
                if (!"ROLE_ADMIN".equals(role)) {
                    log.warn("非管理员用户尝试访问管理后台: userId={}, role={}, path={}", loginId, role, path);
                    return forbidden(exchange, "无权访问管理后台");
                }
            } catch (Exception e) {
                log.error("管理员权限校验失败", e);
                return unauthorized(exchange, "认证失败");
            }
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -90; // 在认证过滤器之后执行
    }

    /**
     * 返回 401 未授权
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeResponse(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    /**
     * 返回 403 禁止访问
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeResponse(exchange, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 写入响应
     */
    private Mono<Void> writeResponse(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        String json = String.format("{\"code\":%d,\"msg\":\"%s\",\"data\":null}", status.value(), message);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
