package com.jelly.cinema.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 多提供商配置属性
 *
 * @author Jelly Cinema
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProviderProperties {

    /**
     * 是否启用 AI 功能
     */
    private boolean enable = true;

    /**
     * 旧版 Key（兼容）
     */
    private String key;

    /**
     * AI 提供商列表
     */
    private List<Provider> providers = new ArrayList<>();

    /**
     * 故障转移配置
     */
    private Failover failover = new Failover();

    /**
     * AI 提供商配置
     */
    @Data
    public static class Provider {
        /**
         * 提供商名称
         */
        private String name;

        /**
         * 优先级（数字越小优先级越高）
         */
        private int priority = 1;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 基础 URL
         */
        private String baseUrl;

        /**
         * 模型名称
         */
        private String model;

        /**
         * 温度
         */
        private double temperature = 0.7;

        /**
         * 最大 Token 数
         */
        private int maxTokens = 2000;

        /**
         * 超时时间（秒）
         */
        private int timeout = 60;
    }

    /**
     * 故障转移配置
     */
    @Data
    public static class Failover {
        /**
         * 熔断器失败阈值
         */
        private int failureThreshold = 5;

        /**
         * 熔断器超时时间（秒）
         */
        private int timeout = 60;

        /**
         * 健康检查间隔（秒）
         */
        private int healthCheckInterval = 30;

        /**
         * 自动重试次数
         */
        private int retryCount = 3;

        /**
         * 重试间隔（毫秒）
         */
        private int retryInterval = 1000;
    }
}
