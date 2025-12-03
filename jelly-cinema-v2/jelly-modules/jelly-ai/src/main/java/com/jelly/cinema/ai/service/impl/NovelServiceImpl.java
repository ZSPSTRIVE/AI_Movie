package com.jelly.cinema.ai.service.impl;

import com.jelly.cinema.ai.domain.dto.NovelChapterDTO;
import com.jelly.cinema.ai.domain.dto.NovelOutlineDTO;
import com.jelly.cinema.ai.service.NovelService;
import com.jelly.cinema.common.redis.service.RedisService;
import dev.langchain4j.data.message.AiMessage;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AI 小说生成服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelServiceImpl implements NovelService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final RedisService redisService;

    private static final String NOVEL_CONTEXT_KEY = "jelly:novel:context:";

    private static final String OUTLINE_SYSTEM_PROMPT = """
            你是一位专业的网络小说作家，擅长创作引人入胜的故事大纲。
            请根据用户提供的主题、风格和主角信息，生成一个详细的小说大纲。
            
            输出格式必须是 JSON，结构如下：
            {
              "title": "小说标题",
              "synopsis": "故事简介（100字左右）",
              "worldSetting": "世界观设定",
              "characters": [
                {"name": "角色名", "role": "主角/配角/反派", "description": "角色描述"}
              ],
              "chapters": [
                {"index": 1, "title": "第一章标题", "summary": "章节摘要"}
              ]
            }
            
            请确保输出的是有效的 JSON 格式。
            """;

    private static final String CHAPTER_SYSTEM_PROMPT = """
            你是一位专业的网络小说作家，正在创作一部小说。
            
            小说大纲：
            {outline}
            
            之前章节的摘要：
            {context}
            
            请根据以上信息，创作第 {chapterIndex} 章的内容。
            要求：
            1. 情节连贯，与大纲保持一致
            2. 语言生动，描写细腻
            3. 对话自然，人物形象鲜明
            4. 字数约 {wordCount} 字
            
            直接输出章节正文，不要包含章节标题。
            """;

    @Override
    public String generateOutline(NovelOutlineDTO dto) {
        String userPrompt = String.format("""
                请为我创作一部小说的大纲：
                - 主题：%s
                - 风格：%s
                - 主角：%s
                - 章节数：%d章
                %s
                """,
                dto.getTheme(),
                dto.getStyle(),
                dto.getProtagonist(),
                dto.getChapterCount(),
                dto.getExtraRequirements() != null ? "- 额外要求：" + dto.getExtraRequirements() : ""
        );

        Response<AiMessage> response = chatLanguageModel.generate(
                List.of(
                        SystemMessage.from(OUTLINE_SYSTEM_PROMPT),
                        UserMessage.from(userPrompt)
                )
        );

        return response.content().text();
    }

    @Override
    public Flux<String> generateChapter(NovelChapterDTO dto) {
        // 获取上下文
        String context = getNovelContext(dto.getBookId());

        String systemPrompt = CHAPTER_SYSTEM_PROMPT
                .replace("{outline}", dto.getOutline())
                .replace("{context}", context != null ? context : "这是小说的开始")
                .replace("{chapterIndex}", String.valueOf(dto.getChapterIndex()))
                .replace("{wordCount}", String.valueOf(dto.getWordCount()));

        String userPrompt = "请创作：" + dto.getChapterTitle();

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullContent = new StringBuilder();

        streamingChatLanguageModel.generate(
                List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                ),
                new dev.langchain4j.model.StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        fullContent.append(token);
                        sink.tryEmitNext(token);
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        // 保存上下文（取最后 2000 字作为摘要）
                        String summary = fullContent.toString();
                        if (summary.length() > 2000) {
                            summary = summary.substring(summary.length() - 2000);
                        }
                        saveNovelContext(dto.getBookId(), 
                                "第" + dto.getChapterIndex() + "章 " + dto.getChapterTitle() + ":\n" + summary);
                        sink.tryEmitComplete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("章节生成错误", error);
                        sink.tryEmitError(error);
                    }
                }
        );

        return sink.asFlux();
    }

    @Override
    public String getNovelContext(String bookId) {
        return redisService.get(NOVEL_CONTEXT_KEY + bookId);
    }

    @Override
    public void saveNovelContext(String bookId, String context) {
        // 保留最近的上下文
        String existing = getNovelContext(bookId);
        String newContext;
        if (existing != null) {
            newContext = existing + "\n\n" + context;
            // 限制总长度
            if (newContext.length() > 10000) {
                newContext = newContext.substring(newContext.length() - 10000);
            }
        } else {
            newContext = context;
        }
        redisService.set(NOVEL_CONTEXT_KEY + bookId, newContext, 7, TimeUnit.DAYS);
    }
}
