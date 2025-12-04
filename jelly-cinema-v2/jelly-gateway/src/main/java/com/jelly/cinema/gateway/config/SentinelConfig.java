package com.jelly.cinema.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;

import java.util.*;

/**
 * Sentinel 网关限流熔断配置
 * 
 * 支持功能：
 * 1. QPS 限流
 * 2. IP 限流
 * 3. 热点参数限流
 * 4. 系统保护
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
public class SentinelConfig {

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public SentinelConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider,
                          ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    /**
     * 配置限流异常处理器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    // 注意：SentinelGatewayFilter 由 Spring Cloud Alibaba 自动配置提供
    // 无需手动定义，否则会产生 Bean 冲突

    /**
     * 初始化限流规则和自定义异常响应
     */
    @PostConstruct
    public void initGatewayRules() {
        log.info("===== 初始化 Sentinel 网关限流规则 =====");
        
        // 初始化 API 分组
        initCustomizedApis();
        
        // 初始化限流规则
        initFlowRules();
        
        // 初始化自定义限流响应
        initBlockHandler();
        
        log.info("===== Sentinel 网关限流规则初始化完成 =====");
    }

    /**
     * 自定义 API 分组
     * 将相关接口归类，便于统一管理限流规则
     */
    private void initCustomizedApis() {
        Set<ApiDefinition> definitions = new HashSet<>();

        // 电影服务 API 组
        ApiDefinition filmApi = new ApiDefinition("film-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/film/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        definitions.add(filmApi);

        // 用户服务 API 组
        ApiDefinition userApi = new ApiDefinition("user-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/user/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                    add(new ApiPathPredicateItem().setPattern("/auth/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        definitions.add(userApi);

        // IM 服务 API 组
        ApiDefinition imApi = new ApiDefinition("im-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/im/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        definitions.add(imApi);

        // AI 服务 API 组（高消耗接口，需要严格限流）
        ApiDefinition aiApi = new ApiDefinition("ai-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/ai/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        definitions.add(aiApi);

        // 社区服务 API 组
        ApiDefinition communityApi = new ApiDefinition("community-api")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/post/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                    add(new ApiPathPredicateItem().setPattern("/comment/**")
                            .setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_PREFIX));
                }});
        definitions.add(communityApi);

        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
        log.info("加载 {} 个 API 分组定义", definitions.size());
    }

    /**
     * 初始化限流规则
     * 
     * 规则类型：
     * 1. QPS 限流 - 每秒请求数
     * 2. 并发线程数限流
     * 3. 热点参数限流
     */
    private void initFlowRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // ============ 基于 API 分组的 QPS 限流 ============
        
        // 电影服务：100 QPS
        rules.add(new GatewayFlowRule("film-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(100)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 用户服务：200 QPS
        rules.add(new GatewayFlowRule("user-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(200)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // IM 服务：300 QPS（聊天消息需要较高并发）
        rules.add(new GatewayFlowRule("im-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(300)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // AI 服务：20 QPS（AI 接口资源消耗大，严格限流）
        rules.add(new GatewayFlowRule("ai-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(20)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // 社区服务：150 QPS
        rules.add(new GatewayFlowRule("community-api")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
                .setCount(150)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS));

        // ============ 基于路由的热点参数限流 ============
        
        // 电影详情接口：按电影 ID 限流（防止热点电影被刷）
        rules.add(new GatewayFlowRule("jelly-film")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(50)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM)
                        .setFieldName("id")
                        .setMatchStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_EXACT)));

        // ============ IP 限流（防止单 IP 恶意攻击）============
        
        // 针对所有接口，单 IP 每秒最多 50 次请求
        rules.add(new GatewayFlowRule("jelly-auth")
                .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID)
                .setCount(30)
                .setIntervalSec(1)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setParamItem(new GatewayParamFlowItem()
                        .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP)));

        GatewayRuleManager.loadRules(rules);
        log.info("加载 {} 条限流规则", rules.size());
    }

    /**
     * 自定义限流响应
     * 当触发限流时，返回统一的 JSON 格式错误响应
     */
    private void initBlockHandler() {
        BlockRequestHandler blockRequestHandler = (exchange, t) -> {
            log.warn("触发限流: uri={}, reason={}", 
                    exchange.getRequest().getURI().getPath(), 
                    t.getMessage());

            Map<String, Object> result = new HashMap<>();
            result.put("code", 429);
            result.put("message", "请求过于频繁，请稍后再试");
            result.put("timestamp", System.currentTimeMillis());
            result.put("path", exchange.getRequest().getURI().getPath());

            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(result));
        };

        GatewayCallbackManager.setBlockHandler(blockRequestHandler);
    }
}
