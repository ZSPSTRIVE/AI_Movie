package com.jelly.cinema.ai.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import com.jelly.cinema.ai.service.AgentChatService;
import com.jelly.cinema.ai.tools.AiMovieTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Agent chat service based on LangChain4j tool-calling.
 */
@Slf4j
@Service("agentChatService")
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final AiMovieTools aiMovieTools;
    private final ObjectMapper objectMapper;

    private volatile List<ToolSpecification> allToolSpecifications;

    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final int MAX_TOOL_COUNT = 20;
    private static final int STREAM_TIMEOUT_SECONDS = 120;

    private static final String AGENT_SYSTEM_PROMPT = """
            你是果冻影院的 AI 电影助手，请准确、简洁、友好地回答用户问题。
            可用工具：
            1) searchMovies：按关键词搜索电影
            2) getMovieDetail：获取电影详情
            3) getRecommendedMovies：获取推荐
            4) getHotMovies：获取热门
            5) ragSearch：知识库检索（当用户问剧情细节、彩蛋、解析时优先使用）

            规则：
            - 提到电影时使用 Markdown 链接格式 [电影名](/film/ID)
            - 不确定的信息请明确说明，不要编造
            - 回答结束时可给出下一步建议（如“要不要我帮你再推荐同类型影片？”）
            """;

    @Override
    public String chat(ChatRequestDTO dto) {
        try {
            return agentChat(dto);
        } catch (Exception e) {
            log.error("Agent chat failed, fallback to plain chat", e);
            return fallbackChat(dto);
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequestDTO dto) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        Thread worker = new Thread(() -> streamAgentChat(dto, sink), "agent-chat-stream");
        worker.setDaemon(true);
        worker.start();
        return sink.asFlux();
    }

    private void streamAgentChat(ChatRequestDTO dto, Sinks.Many<String> sink) {
        try {
            boolean ragEnabled = Boolean.TRUE.equals(dto.getEnableRag());
            List<ToolSpecification> toolSpecifications = getToolSpecifications(ragEnabled);
            List<ChatMessage> messages = buildMessages(dto);

            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                Response<AiMessage> response = chatLanguageModel.generate(messages, toolSpecifications);
                AiMessage aiMessage = response.content();

                if (!aiMessage.hasToolExecutionRequests()) {
                    // True token streaming: stream a fresh completion from current context.
                    streamFinalAnswer(messages, sink);
                    return;
                }

                messages.add(aiMessage);
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    sink.tryEmitNext(String.format("[tool:%s] running\n", request.name()));
                    String toolResult = executeToolByName(request.name(), request.arguments(), ragEnabled);
                    messages.add(ToolExecutionResultMessage.from(request, toolResult));
                    sink.tryEmitNext(String.format("[tool:%s] done\n", request.name()));
                }
            }

            sink.tryEmitNext("抱歉，处理过程较复杂，请尝试简化您的问题。");
            sink.tryEmitComplete();
        } catch (Exception e) {
            log.error("Agent streaming failed", e);
            sink.tryEmitError(e);
        }
    }

    private void streamFinalAnswer(List<ChatMessage> messages, Sinks.Many<String> sink) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        streamingChatLanguageModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                sink.tryEmitNext(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                sink.tryEmitComplete();
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorRef.set(error);
                sink.tryEmitError(error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("Streaming response timeout");
            }
            if (errorRef.get() != null) {
                throw new RuntimeException("Streaming response failed", errorRef.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Streaming interrupted", e);
        }
    }

    private String agentChat(ChatRequestDTO dto) {
        boolean ragEnabled = Boolean.TRUE.equals(dto.getEnableRag());
        List<ToolSpecification> toolSpecifications = getToolSpecifications(ragEnabled);
        List<ChatMessage> messages = buildMessages(dto);

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            Response<AiMessage> response = chatLanguageModel.generate(messages, toolSpecifications);
            AiMessage aiMessage = response.content();
            messages.add(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                return aiMessage.text();
            }

            // 对工具调用轮数做硬限制，避免 Agent 在异常场景下无限递归调用工具。
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                String toolResult = executeToolByName(request.name(), request.arguments(), ragEnabled);
                messages.add(ToolExecutionResultMessage.from(request, toolResult));
            }
        }

        log.warn("Agent reached max tool iterations");
        return "抱歉，处理过程较复杂，请尝试简化您的问题。";
    }

    private List<ChatMessage> buildMessages(ChatRequestDTO dto) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(AGENT_SYSTEM_PROMPT));

        if (dto.getHistory() != null) {
            for (ChatRequestDTO.Message msg : dto.getHistory()) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }

        messages.add(UserMessage.from(dto.getPrompt() == null ? "" : dto.getPrompt()));
        return messages;
    }

    private List<ToolSpecification> getToolSpecifications(boolean ragEnabled) {
        if (allToolSpecifications == null) {
            synchronized (this) {
                if (allToolSpecifications == null) {
                    allToolSpecifications = ToolSpecifications.toolSpecificationsFrom(aiMovieTools);
                    log.info("Loaded {} tool specifications", allToolSpecifications.size());
                }
            }
        }

        if (ragEnabled) {
            return allToolSpecifications;
        }
        return allToolSpecifications.stream()
                .filter(spec -> !"ragSearch".equals(spec.name()))
                .collect(Collectors.toList());
    }

    private String executeToolByName(String toolName, String argsJson, boolean ragEnabled) {
        try {
            return switch (toolName) {
                case "searchMovies" -> {
                    SearchMoviesArgs args = parseArgs(argsJson, SearchMoviesArgs.class);
                    yield aiMovieTools.searchMovies(requireText(args.keyword, "keyword"));
                }
                case "getMovieDetail" -> {
                    MovieDetailArgs args = parseArgs(argsJson, MovieDetailArgs.class);
                    yield aiMovieTools.getMovieDetail(requirePositiveId(args.filmId, "filmId"));
                }
                case "getRecommendedMovies" -> {
                    CountArgs args = parseArgs(argsJson, CountArgs.class);
                    yield aiMovieTools.getRecommendedMovies(normalizeCount(args.count, 5));
                }
                case "getHotMovies" -> {
                    CountArgs args = parseArgs(argsJson, CountArgs.class);
                    yield aiMovieTools.getHotMovies(normalizeCount(args.count, 10));
                }
                case "ragSearch" -> {
                    if (!ragEnabled) {
                        yield "当前请求未开启 RAG（enableRag=false），已跳过知识库检索。";
                    }
                    RagSearchArgs args = parseArgs(argsJson, RagSearchArgs.class);
                    yield aiMovieTools.ragSearch(requireText(args.query, "query"));
                }
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {} | {}", toolName, e.getMessage(), e);
            return "工具执行出错: " + e.getMessage();
        }
    }

    private <T> T parseArgs(String argsJson, Class<T> clazz) throws Exception {
        if (argsJson == null || argsJson.isBlank()) {
            return objectMapper.readValue("{}", clazz);
        }
        return objectMapper.readValue(argsJson, clazz);
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private Long requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be a positive integer");
        }
        return value;
    }

    private int normalizeCount(Integer count, int defaultValue) {
        int normalized = count == null ? defaultValue : count;
        if (normalized <= 0) {
            normalized = defaultValue;
        }
        return Math.min(normalized, MAX_TOOL_COUNT);
    }

    private String fallbackChat(ChatRequestDTO dto) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(AGENT_SYSTEM_PROMPT));
        messages.add(UserMessage.from(dto.getPrompt() == null ? "" : dto.getPrompt()));
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SearchMoviesArgs {
        public String keyword;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class MovieDetailArgs {
        public Long filmId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CountArgs {
        public Integer count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RagSearchArgs {
        public String query;
    }
}
