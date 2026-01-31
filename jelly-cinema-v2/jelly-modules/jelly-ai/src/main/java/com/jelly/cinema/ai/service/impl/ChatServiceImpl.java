package com.jelly.cinema.ai.service.impl;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import com.jelly.cinema.ai.service.ChatService;
import com.jelly.cinema.ai.service.RagService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
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

/**
 * AI 对话服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final RagService ragService;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是果冻影院的 AI 助手，一个友好、专业的电影知识专家。
            你可以帮助用户解答关于电影、演员、导演、剧情等问题。
            请用简洁、准确的语言回答问题。如果不确定，请诚实地说不知道。
            """;

    private static final String RAG_SYSTEM_PROMPT = """
            你是一个电影知识专家。请根据以下已知信息回答用户问题。
            如果无法从信息中得到答案，请说"抱歉，我没有找到相关信息"，不要编造。
            
            已知信息:
            {context}
            ---
            """;

    private static final String FILM_CONTEXT_PROMPT = """
            你是果冻影院的 AI 影评助手，正在帮助用户了解以下电影：
            
            电影名称：{title}
            类型：{category}
            地区：{region}
            年份：{year}
            导演：{director}
            主演：{actors}
            剧情简介：{description}
            
            ---
            请基于以上信息回答用户的问题。你可以：
            1. 解析剧情要点和看点
            2. 介绍演员和导演背景（如果你知道）
            3. 分析影片类型和风格
            4. 推荐类似电影
            
            如果某些信息缺失，可以基于电影标题、类型、年份做合理推测，但要明确告知用户这是推测。
            保持回答简洁、准确、友好。
            """;

    @Override
    public String chat(ChatRequestDTO dto) {
        List<ChatMessage> messages = buildMessages(dto);
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }

    @Override
    public Flux<String> chatStream(ChatRequestDTO dto) {
        List<ChatMessage> messages = buildMessages(dto);

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        streamingChatLanguageModel.generate(messages, new dev.langchain4j.model.StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                sink.tryEmitNext(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式对话错误", error);
                sink.tryEmitError(error);
            }
        });

        return sink.asFlux();
    }

    /**
     * 构建消息列表
     */
    private List<ChatMessage> buildMessages(ChatRequestDTO dto) {
        List<ChatMessage> messages = new ArrayList<>();

        // 系统提示词 - 优先级：电影上下文 > RAG > 默认
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        
        // 1. 如果有电影上下文，使用电影专用提示词
        if (dto.getFilmContext() != null) {
            ChatRequestDTO.FilmContext ctx = dto.getFilmContext();
            systemPrompt = FILM_CONTEXT_PROMPT
                .replace("{title}", ctx.getTitle() != null ? ctx.getTitle() : "未知")
                .replace("{category}", ctx.getCategory() != null ? ctx.getCategory() : "未知")
                .replace("{region}", ctx.getRegion() != null ? ctx.getRegion() : "未知")
                .replace("{year}", ctx.getYear() != null ? ctx.getYear() : "未知")
                .replace("{director}", ctx.getDirector() != null ? ctx.getDirector() : "未知")
                .replace("{actors}", ctx.getActors() != null ? ctx.getActors() : "未知")
                .replace("{description}", ctx.getDescription() != null ? ctx.getDescription() : "暂无剧情简介");
        } 
        // 2. 否则如果启用RAG，使用RAG检索
        else if (Boolean.TRUE.equals(dto.getEnableRag())) {
            String context = ragService.retrieve(dto.getPrompt());
            systemPrompt = RAG_SYSTEM_PROMPT.replace("{context}", context);
        }
        
        messages.add(SystemMessage.from(systemPrompt));

        // 历史对话
        if (dto.getHistory() != null) {
            for (ChatRequestDTO.Message msg : dto.getHistory()) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }

        // 当前问题
        messages.add(UserMessage.from(dto.getPrompt()));

        return messages;
    }
}
