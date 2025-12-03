package com.jelly.cinema.common.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置
 * 
 * 注意：下游微服务不再进行 Token 验证
 * 认证由 Gateway 统一处理，下游服务信任 Gateway 传递的用户信息
 *
 * @author Jelly Cinema
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    
    // Gateway 已经完成了认证，下游服务不需要再次验证 Token
    // 用户信息通过请求头 X-User-Id 和 X-Username 传递
}
