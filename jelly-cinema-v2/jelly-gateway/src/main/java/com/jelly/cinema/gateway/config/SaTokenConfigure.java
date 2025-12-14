package com.jelly.cinema.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 网关鉴权配置
 *
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
public class SaTokenConfigure {

    /**
     * 白名单路径 - 无需登录即可访问
     */
    private static final String[] WHITE_LIST = {
            // 管理后台（临时放开，后续再完善权限）
            "/admin/**",
            // 认证相关
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            "/auth/email/code",
            "/auth/check/email-verify",
            "/auth/login/email-code",
            // 电影模块 - 全部公开
            "/film/**",
            // 社区模块 - 全部公开
            "/post/**",
            "/comment/**",
            // AI 模块 - 全部公开
            "/ai/**",
            // IM 模块 - 全部公开
            "/ws/**",
            "/im/**",
            // API 文档
            "/doc.html",
            "/webjars/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/*/v3/api-docs/**",
            // 健康检查
            "/actuator/**"
    };

    /**
     * 注册 Sa-Token 全局过滤器
     */
    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截所有路径
                .addInclude("/**")
                // 排除白名单
                .addExclude(WHITE_LIST)
                // 前置函数：在认证之前执行
                .setBeforeAuth(obj -> {
                    // 不做任何操作，让白名单路径直接通过
                })
                // 鉴权规则
                .setAuth(obj -> {
                    // 登录校验 - 仅对非白名单路径生效
                    SaRouter.match("/**", r -> StpUtil.checkLogin());
                    // 管理后台权限由 AdminAuthFilter 统一处理，这里不再重复 checkRole
                })
                // 异常处理
                .setError(e -> {
                    log.error("Sa-Token error: {}", e.getMessage());
                    return SaResult.error(e.getMessage());
                });
    }
}
