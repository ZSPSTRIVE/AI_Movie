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

        // 系统提示词
        String systemPrompt = DEFAULT_SYSTEM_PROMPT;
        if (Boolean.TRUE.equals(dto.getEnableRag())) {
            // RAG 检索
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
