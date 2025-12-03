package com.jelly.cinema.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置（旧版 - 已弃用，使用 MultiProviderAiConfig）
 *
 * @author Jelly Cinema
 * @deprecated 使用 {@link MultiProviderAiConfig} 替代
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.openai", name = "api-key")
@Deprecated
public class AiConfig {

    @Value("${ai.openai.api-key:sk-xxx}")
    private String apiKey;

    @Value("${ai.openai.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${ai.openai.model:deepseek-chat}")
    private String model;

    @Value("${ai.openai.embedding-model:BAAI/bge-m3}")
    private String embeddingModel;

    /**
     * Chat 模型（同步）
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(0.7)
                .maxTokens(4096)
                .build();
    }

    /**
     * Chat 模型（流式）
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(0.7)
                .build();
    }

    /**
     * Embedding 模型
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }
}
