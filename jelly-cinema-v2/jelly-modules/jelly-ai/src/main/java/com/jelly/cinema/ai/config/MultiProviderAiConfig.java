package com.jelly.cinema.ai.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多 AI 提供商配置（支持故障转移）
 *
 * @author Jelly Cinema
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enable", havingValue = "true", matchIfMissing = true)
public class MultiProviderAiConfig {

    private final AiProviderProperties properties;

    /**
     * 提供商健康状态
     */
    private final ConcurrentHashMap<String, ProviderHealth> healthStatus = new ConcurrentHashMap<>();

    /**
     * 获取当前可用的提供商
     */
    private AiProviderProperties.Provider getActiveProvider() {
        List<AiProviderProperties.Provider> providers = properties.getProviders().stream()
                .filter(AiProviderProperties.Provider::isEnabled)
                .filter(p -> isHealthy(p.getName()))
                .sorted(Comparator.comparingInt(AiProviderProperties.Provider::getPriority))
                .toList();

        if (providers.isEmpty()) {
            // 如果没有健康的提供商，使用第一个启用的
            return properties.getProviders().stream()
                    .filter(AiProviderProperties.Provider::isEnabled)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("没有可用的 AI 提供商"));
        }

        return providers.get(0);
    }

    /**
     * 检查提供商是否健康
     */
    private boolean isHealthy(String providerName) {
        ProviderHealth health = healthStatus.get(providerName);
        if (health == null) {
            return true;
        }
        
        // 如果失败次数超过阈值，检查是否已经过了熔断超时时间
        if (health.failureCount.get() >= properties.getFailover().getFailureThreshold()) {
            long elapsed = System.currentTimeMillis() - health.lastFailureTime;
            if (elapsed > properties.getFailover().getTimeout() * 1000L) {
                // 重置健康状态
                health.failureCount.set(0);
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 记录提供商失败
     */
    public void recordFailure(String providerName) {
        healthStatus.computeIfAbsent(providerName, k -> new ProviderHealth())
                .recordFailure();
        log.warn("AI 提供商 {} 请求失败，当前失败次数: {}", 
                providerName, 
                healthStatus.get(providerName).failureCount.get());
    }

    /**
     * 记录提供商成功
     */
    public void recordSuccess(String providerName) {
        ProviderHealth health = healthStatus.get(providerName);
        if (health != null) {
            health.failureCount.set(0);
        }
    }

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        AiProviderProperties.Provider provider = getActiveProvider();
        log.info("使用 AI 提供商: {} ({})", provider.getName(), provider.getModel());
        
        return OpenAiChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName(provider.getModel())
                .temperature(provider.getTemperature())
                .maxTokens(provider.getMaxTokens())
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                .build();
    }

    @Bean
    @Primary
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        AiProviderProperties.Provider provider = getActiveProvider();
        
        return OpenAiStreamingChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName(provider.getModel())
                .temperature(provider.getTemperature())
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                // 使用自定义 tokenizer 避免模型不识别问题
                .tokenizer(new SimpleTokenizer())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
    
    /**
     * 简单的 Tokenizer 实现，用于不被 jtokkit 支持的模型
     */
    private static class SimpleTokenizer implements Tokenizer {
        @Override
        public int estimateTokenCountInText(String text) {
            // 简单估算：每4个字符约等于1个token
            return text != null ? text.length() / 4 + 1 : 0;
        }
        
        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return estimateTokenCountInText(message.toString());
        }
        
        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int count = 0;
            for (ChatMessage msg : messages) {
                count += estimateTokenCountInMessage(msg);
            }
            return count;
        }
        
        @Override
        public int estimateTokenCountInToolExecutionRequests(Iterable<dev.langchain4j.agent.tool.ToolExecutionRequest> toolExecutionRequests) {
            int count = 0;
            for (dev.langchain4j.agent.tool.ToolExecutionRequest req : toolExecutionRequests) {
                count += estimateTokenCountInText(req.toString());
            }
            return count;
        }
        
        @Override
        public int estimateTokenCountInToolSpecifications(Iterable<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications) {
            int count = 0;
            for (dev.langchain4j.agent.tool.ToolSpecification spec : toolSpecifications) {
                count += estimateTokenCountInText(spec.toString());
            }
            return count;
        }
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        // 使用硅基流动的 embedding 模型
        AiProviderProperties.Provider provider = properties.getProviders().stream()
                .filter(p -> "siliconflow".equals(p.getName()) && p.isEnabled())
                .findFirst()
                .orElseGet(this::getActiveProvider);
        
        return OpenAiEmbeddingModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName("BAAI/bge-m3")
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                .build();
    }

    /**
     * 提供商健康状态
     */
    private static class ProviderHealth {
        AtomicInteger failureCount = new AtomicInteger(0);
        volatile long lastFailureTime = 0;

        void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }
    }
}
