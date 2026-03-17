package com.jelly.cinema.ai.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Multi-provider AI config with runtime failover.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai", name = "enable", havingValue = "true", matchIfMissing = true)
public class MultiProviderAiConfig {

    private final AiProviderProperties properties;

    private final ConcurrentHashMap<String, ProviderHealth> healthStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ChatLanguageModel> chatModels = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StreamingChatLanguageModel> streamingModels = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel() {
        InvocationHandler handler = this::invokeChatMethodWithFailover;
        return (ChatLanguageModel) Proxy.newProxyInstance(
                ChatLanguageModel.class.getClassLoader(),
                new Class[]{ChatLanguageModel.class},
                handler
        );
    }

    @Bean
    @Primary
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        InvocationHandler handler = this::invokeStreamingMethodWithFailover;
        return (StreamingChatLanguageModel) Proxy.newProxyInstance(
                StreamingChatLanguageModel.class.getClassLoader(),
                new Class[]{StreamingChatLanguageModel.class},
                handler
        );
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        AiProviderProperties.Provider provider = getEnabledProviders().stream()
                .filter(p -> "siliconflow".equalsIgnoreCase(p.getName()))
                .filter(this::hasEmbeddingConfig)
                .findFirst()
                .or(() -> getEnabledProviders().stream().filter(this::hasEmbeddingConfig).findFirst())
                .orElse(null);

        if (provider == null) {
            log.warn("No provider with valid embedding config found, fallback to disabled embedding model");
            return new DisabledEmbeddingModel();
        }

        return OpenAiEmbeddingModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName("BAAI/bge-m3")
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                .build();
    }

    private Object invokeChatMethodWithFailover(Object proxy, Method method, Object[] args) throws Throwable {
        if (isObjectMethod(method)) {
            return method.invoke(this, args);
        }

        Throwable lastError = null;
        int retryCount = Math.max(1, properties.getFailover().getRetryCount());
        List<AiProviderProperties.Provider> candidates = getCandidateProviders();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No AI provider with valid apiKey/baseUrl/model is configured");
        }

        for (AiProviderProperties.Provider provider : candidates) {
            ChatLanguageModel model = chatModels.computeIfAbsent(provider.getName(), key -> createChatModel(provider));

            for (int attempt = 1; attempt <= retryCount; attempt++) {
                try {
                    Object result = method.invoke(model, args);
                    recordSuccess(provider.getName());
                    return result;
                } catch (InvocationTargetException ex) {
                    lastError = ex.getTargetException();
                    recordFailure(provider.getName());
                    if (attempt < retryCount) {
                        sleepRetry(provider.getName(), attempt, lastError);
                    }
                } catch (Exception ex) {
                    lastError = ex;
                    recordFailure(provider.getName());
                    if (attempt < retryCount) {
                        sleepRetry(provider.getName(), attempt, lastError);
                    }
                }
            }
        }

        throw new IllegalStateException("No available AI provider for chat request", lastError);
    }

    @SuppressWarnings("unchecked")
    private Object invokeStreamingMethodWithFailover(Object proxy, Method method, Object[] args) throws Throwable {
        if (isObjectMethod(method)) {
            return method.invoke(this, args);
        }

        Throwable lastError = null;
        int retryCount = Math.max(1, properties.getFailover().getRetryCount());
        List<AiProviderProperties.Provider> candidates = getCandidateProviders();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No AI provider with valid apiKey/baseUrl/model is configured");
        }

        for (AiProviderProperties.Provider provider : candidates) {
            StreamingChatLanguageModel model = streamingModels.computeIfAbsent(
                    provider.getName(),
                    key -> createStreamingChatModel(provider)
            );

            for (int attempt = 1; attempt <= retryCount; attempt++) {
                Object[] effectiveArgs = args == null ? new Object[0] : args.clone();
                int handlerIndex = findStreamingHandlerArgIndex(method);
                if (handlerIndex >= 0 && effectiveArgs[handlerIndex] instanceof StreamingResponseHandler<?> original) {
                    effectiveArgs[handlerIndex] = wrapStreamingHandler(
                            provider.getName(),
                            (StreamingResponseHandler<Object>) original
                    );
                }

                try {
                    Object result = method.invoke(model, effectiveArgs);
                    if (handlerIndex < 0) {
                        recordSuccess(provider.getName());
                    }
                    return result;
                } catch (InvocationTargetException ex) {
                    lastError = ex.getTargetException();
                    recordFailure(provider.getName());
                    if (attempt < retryCount) {
                        sleepRetry(provider.getName(), attempt, lastError);
                    }
                } catch (Exception ex) {
                    lastError = ex;
                    recordFailure(provider.getName());
                    if (attempt < retryCount) {
                        sleepRetry(provider.getName(), attempt, lastError);
                    }
                }
            }
        }

        throw new IllegalStateException("No available AI provider for streaming request", lastError);
    }

    private boolean isObjectMethod(Method method) {
        return method.getDeclaringClass() == Object.class;
    }

    private int findStreamingHandlerArgIndex(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (StreamingResponseHandler.class.isAssignableFrom(parameterTypes[i])) {
                return i;
            }
        }
        return -1;
    }

    private <T> StreamingResponseHandler<T> wrapStreamingHandler(
            String providerName,
            StreamingResponseHandler<T> delegate
    ) {
        return new StreamingResponseHandler<>() {
            @Override
            public void onNext(String token) {
                delegate.onNext(token);
            }

            @Override
            public void onComplete(dev.langchain4j.model.output.Response<T> response) {
                recordSuccess(providerName);
                delegate.onComplete(response);
            }

            @Override
            public void onError(Throwable error) {
                recordFailure(providerName);
                delegate.onError(error);
            }
        };
    }

    private ChatLanguageModel createChatModel(AiProviderProperties.Provider provider) {
        log.info("Init chat provider model: {} ({})", provider.getName(), provider.getModel());
        return OpenAiChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName(provider.getModel())
                .temperature(provider.getTemperature())
                .maxTokens(provider.getMaxTokens())
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                .build();
    }

    private StreamingChatLanguageModel createStreamingChatModel(AiProviderProperties.Provider provider) {
        log.info("Init streaming provider model: {} ({})", provider.getName(), provider.getModel());
        return OpenAiStreamingChatModel.builder()
                .apiKey(provider.getApiKey())
                .baseUrl(provider.getBaseUrl())
                .modelName(provider.getModel())
                .temperature(provider.getTemperature())
                .timeout(Duration.ofSeconds(provider.getTimeout()))
                .tokenizer(new SimpleTokenizer())
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private void sleepRetry(String providerName, int attempt, Throwable lastError) {
        long retryIntervalMs = Math.max(0, properties.getFailover().getRetryInterval());
        log.warn(
                "Provider {} failed on attempt {}, retry after {}ms. cause={}",
                providerName,
                attempt,
                retryIntervalMs,
                lastError == null ? "unknown" : lastError.getMessage()
        );
        if (retryIntervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<AiProviderProperties.Provider> getCandidateProviders() {
        List<AiProviderProperties.Provider> enabled = getEnabledProviders();
        List<AiProviderProperties.Provider> healthy = enabled.stream()
                .filter(provider -> isHealthy(provider.getName()))
                .toList();
        return healthy.isEmpty() ? enabled : healthy;
    }

    private List<AiProviderProperties.Provider> getEnabledProviders() {
        List<AiProviderProperties.Provider> providers = properties.getProviders().stream()
                .filter(AiProviderProperties.Provider::isEnabled)
                .filter(this::hasChatConfig)
                .sorted(Comparator.comparingInt(AiProviderProperties.Provider::getPriority))
                .toList();
        if (providers.isEmpty()) {
            log.warn("No enabled AI provider with valid chat config found");
        }
        return providers;
    }

    private AiProviderProperties.Provider getHighestPriorityProvider() {
        return getEnabledProviders().get(0);
    }

    private boolean hasEmbeddingConfig(AiProviderProperties.Provider provider) {
        return StringUtils.hasText(provider.getApiKey()) && StringUtils.hasText(provider.getBaseUrl());
    }

    private boolean hasChatConfig(AiProviderProperties.Provider provider) {
        return StringUtils.hasText(provider.getApiKey())
                && StringUtils.hasText(provider.getBaseUrl())
                && StringUtils.hasText(provider.getModel());
    }

    private boolean isHealthy(String providerName) {
        ProviderHealth health = healthStatus.get(providerName);
        if (health == null) {
            return true;
        }
        if (health.failureCount.get() < properties.getFailover().getFailureThreshold()) {
            return true;
        }
        long elapsedMs = System.currentTimeMillis() - health.lastFailureTime;
        if (elapsedMs > properties.getFailover().getTimeout() * 1000L) {
            health.failureCount.set(0);
            return true;
        }
        return false;
    }

    public void recordFailure(String providerName) {
        ProviderHealth health = healthStatus.computeIfAbsent(providerName, key -> new ProviderHealth());
        health.recordFailure();
        log.warn(
                "AI provider {} request failed, failureCount={}",
                providerName,
                health.failureCount.get()
        );
    }

    public void recordSuccess(String providerName) {
        ProviderHealth health = healthStatus.get(providerName);
        if (health != null) {
            health.failureCount.set(0);
        }
    }

    /**
     * Minimal tokenizer for unsupported models in jtkkit.
     */
    private static class SimpleTokenizer implements Tokenizer {
        @Override
        public int estimateTokenCountInText(String text) {
            return text != null ? text.length() / 4 + 1 : 0;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            return estimateTokenCountInText(message == null ? "" : message.toString());
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int count = 0;
            for (ChatMessage message : messages) {
                count += estimateTokenCountInMessage(message);
            }
            return count;
        }

        @Override
        public int estimateTokenCountInToolExecutionRequests(
                Iterable<dev.langchain4j.agent.tool.ToolExecutionRequest> toolExecutionRequests
        ) {
            int count = 0;
            for (dev.langchain4j.agent.tool.ToolExecutionRequest request : toolExecutionRequests) {
                count += estimateTokenCountInText(request.toString());
            }
            return count;
        }

        @Override
        public int estimateTokenCountInToolSpecifications(
                Iterable<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications
        ) {
            int count = 0;
            for (dev.langchain4j.agent.tool.ToolSpecification specification : toolSpecifications) {
                count += estimateTokenCountInText(specification.toString());
            }
            return count;
        }
    }

    private static class ProviderHealth {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private volatile long lastFailureTime = 0L;

        void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime = System.currentTimeMillis();
        }
    }

    /**
     * Degraded embedding model used when no API key is configured.
     * Returns deterministic zero vectors to keep service startup non-blocking.
     */
    private static class DisabledEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSION = 1024;

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            if (segments == null || segments.isEmpty()) {
                return Response.from(List.of());
            }
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> Embedding.from(new float[DIMENSION]))
                    .toList();
            return Response.from(embeddings);
        }
    }
}
