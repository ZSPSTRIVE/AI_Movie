package com.jelly.cinema.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Sentinel + Nacos 动态规则配置
 * 
 * 支持从 Nacos 动态推送规则：
 * 1. 限流规则 (flow rules)
 * 2. 熔断降级规则 (degrade rules)
 * 3. 系统保护规则 (system rules)
 * 
 * Nacos 配置示例:
 * Data ID: sentinel-gateway-flow-rules
 * Group: SENTINEL_GROUP
 * 
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
public class SentinelNacosConfig {

    @Value("${spring.cloud.nacos.config.server-addr:localhost:8848}")
    private String nacosServerAddr;

    @Value("${spring.cloud.nacos.config.namespace:}")
    private String nacosNamespace;

    private static final String SENTINEL_GROUP = "SENTINEL_GROUP";

    // Nacos Data ID
    private static final String FLOW_RULES_DATA_ID = "sentinel-gateway-flow-rules";
    private static final String DEGRADE_RULES_DATA_ID = "sentinel-degrade-rules";
    private static final String SYSTEM_RULES_DATA_ID = "sentinel-system-rules";

    @PostConstruct
    public void initNacosDataSource() {
        log.info("===== 初始化 Sentinel Nacos 动态数据源 =====");
        log.info("Nacos Server: {}", nacosServerAddr);

        Properties properties = new Properties();
        properties.put("serverAddr", nacosServerAddr);
        if (nacosNamespace != null && !nacosNamespace.isEmpty()) {
            properties.put("namespace", nacosNamespace);
        }

        // 初始化网关限流规则数据源
        initGatewayFlowRulesDataSource(properties);

        // 初始化熔断降级规则数据源
        initDegradeRulesDataSource(properties);

        // 初始化系统保护规则数据源
        initSystemRulesDataSource(properties);

        log.info("===== Sentinel Nacos 数据源初始化完成 =====");
    }

    /**
     * 网关限流规则 - 从 Nacos 动态获取
     * 
     * 配置示例 (JSON):
     * [
     *   {
     *     "resource": "film-api",
     *     "resourceMode": 1,
     *     "count": 100,
     *     "intervalSec": 1,
     *     "grade": 1
     *   }
     * ]
     */
    private void initGatewayFlowRulesDataSource(Properties properties) {
        try {
            ReadableDataSource<String, Set<GatewayFlowRule>> flowRulesDataSource = new NacosDataSource<>(
                    properties,
                    SENTINEL_GROUP,
                    FLOW_RULES_DATA_ID,
                    source -> {
                        if (source == null || source.isEmpty()) {
                            return null;
                        }
                        return JSON.parseObject(source, new TypeReference<Set<GatewayFlowRule>>() {});
                    }
            );
            GatewayRuleManager.register2Property(flowRulesDataSource.getProperty());
            log.info("注册网关限流规则数据源: dataId={}", FLOW_RULES_DATA_ID);
        } catch (Exception e) {
            log.warn("网关限流规则数据源初始化失败，将使用本地默认规则: {}", e.getMessage());
        }
    }

    /**
     * 熔断降级规则 - 从 Nacos 动态获取
     * 
     * 配置示例 (JSON):
     * [
     *   {
     *     "resource": "film-api",
     *     "grade": 0,
     *     "count": 5,
     *     "slowRatioThreshold": 0.5,
     *     "timeWindow": 30,
     *     "minRequestAmount": 10,
     *     "statIntervalMs": 1000
     *   }
     * ]
     */
    private void initDegradeRulesDataSource(Properties properties) {
        try {
            ReadableDataSource<String, List<DegradeRule>> degradeRulesDataSource = new NacosDataSource<>(
                    properties,
                    SENTINEL_GROUP,
                    DEGRADE_RULES_DATA_ID,
                    source -> {
                        if (source == null || source.isEmpty()) {
                            return null;
                        }
                        return JSON.parseObject(source, new TypeReference<List<DegradeRule>>() {});
                    }
            );
            DegradeRuleManager.register2Property(degradeRulesDataSource.getProperty());
            log.info("注册熔断降级规则数据源: dataId={}", DEGRADE_RULES_DATA_ID);
        } catch (Exception e) {
            log.warn("熔断降级规则数据源初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 系统保护规则 - 从 Nacos 动态获取
     * 
     * 配置示例 (JSON):
     * [
     *   {
     *     "highestSystemLoad": 5.0,
     *     "highestCpuUsage": 0.9,
     *     "avgRt": 100,
     *     "maxThread": 500,
     *     "qps": 1000
     *   }
     * ]
     */
    private void initSystemRulesDataSource(Properties properties) {
        try {
            ReadableDataSource<String, List<SystemRule>> systemRulesDataSource = new NacosDataSource<>(
                    properties,
                    SENTINEL_GROUP,
                    SYSTEM_RULES_DATA_ID,
                    source -> {
                        if (source == null || source.isEmpty()) {
                            return null;
                        }
                        return JSON.parseObject(source, new TypeReference<List<SystemRule>>() {});
                    }
            );
            SystemRuleManager.register2Property(systemRulesDataSource.getProperty());
            log.info("注册系统保护规则数据源: dataId={}", SYSTEM_RULES_DATA_ID);
        } catch (Exception e) {
            log.warn("系统保护规则数据源初始化失败: {}", e.getMessage());
        }
    }
}
