package com.jelly.cinema.ai.service.impl;

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 模式对话服务实现
 * 基于 LangChain4j 实现自动工具调用
 * 
 * 工作流程:
 * 1. 接收用户消息
 * 2. AI 判断是否需要调用工具
 * 3. 如需要，执行工具并将结果返回给 AI
 * 4. AI 基于工具结果生成最终回复
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Service("agentChatService")
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final AiMovieTools aiMovieTools;

    /** 工具规格列表 - 从 AiMovieTools 的 @Tool 注解生成 */
    private List<ToolSpecification> toolSpecifications;

    /** 最大工具调用轮次，防止无限循环 */
    private static final int MAX_TOOL_ITERATIONS = 5;

    private static final String AGENT_SYSTEM_PROMPT = """
            你是果冻影院的 AI 智能助手，一个幽默、专业且热情的电影专家。
            你的目标是帮助用户发现好电影，或者解决他们关于电影的问题。
            
            🛠️ 你的工具箱 (请灵活使用):
            1. **searchMovies**: 找电影首选。根据名称、演员、导演搜索。
            2. **getRecommendedMovies**: 用户不知道看什么时，推荐高分好片。
            3. **getHotMovies**: 用户想看这一周最火的片子时使用。
            4. **getMovieDetail**: 获取特定电影的详细信息（剧情、演员表等）。
            5. **ragSearch**: 🧠 知识库检索。当用户问剧情细节、彩蛋、幕后故事、影评解析时，**必须**优先调用此工具查阅知识库。
            
            📝 回复规则 (务必遵守):
            1. **格式化链接**: 提到任何电影时，必须使用 Markdown 链接格式 `[电影名](/film/ID)`，这样用户点击就能直接播放！例如：推荐你看 [星际穿越](/film/123)。
            2. **多态回复**: 不要每次都说一样的开场白。根据用户的语气调整（幽默、正式、简洁）。
            3. **RAG 优先**: 遇到"讲讲剧情"、"解析一下"、"结局是什么"这类问题，不要瞎编，先用 `ragSearch` 查。
            4. **行动导向**: 推荐完电影后，可以顺便引导用户："是否需要我为您播放？" 或者 "想了解更多关于导演的信息吗？"
            
            当前时间：{{current_date}}
            """;

    /**
     * 懒加载工具规格
     */
    private List<ToolSpecification> getToolSpecifications() {
        if (toolSpecifications == null) {
            toolSpecifications = ToolSpecifications.toolSpecificationsFrom(aiMovieTools);
            log.info("📋 Loaded {} tool specifications", toolSpecifications.size());
        }
        return toolSpecifications;
    }

    @Override
    public String chat(ChatRequestDTO dto) {
        try {
            return agentChat(dto);
        } catch (Exception e) {
            log.error("Agent 对话失败，降级为普通对话", e);
            return fallbackChat(dto);
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequestDTO dto) {
        // Agent 模式的流式响应（简化版：先获取完整响应再流式输出）
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        // 异步执行 Agent 对话
        new Thread(() -> {
            try {
                String response = agentChat(dto);
                // 模拟流式输出
                for (char c : response.toCharArray()) {
                    sink.tryEmitNext(String.valueOf(c));
                    Thread.sleep(10); // 模拟打字效果
                }
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.error("Agent 流式对话失败", e);
                sink.tryEmitError(e);
            }
        }).start();
        
        return sink.asFlux();
    }

    /**
     * Agent 对话核心逻辑
     * 实现 ReAct (Reasoning + Acting) 模式
     */
    private String agentChat(ChatRequestDTO dto) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // 添加系统提示
        messages.add(SystemMessage.from(AGENT_SYSTEM_PROMPT));
        
        // 添加历史对话
        if (dto.getHistory() != null) {
            for (ChatRequestDTO.Message msg : dto.getHistory()) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }
        
        // 添加当前问题
        messages.add(UserMessage.from(dto.getPrompt()));
        
        // 迭代执行工具调用
        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            log.debug("🔄 Agent iteration {} - Messages: {}", i + 1, messages.size());
            
            // 调用 LLM（带工具）
            Response<AiMessage> response = chatLanguageModel.generate(
                    messages,
                    getToolSpecifications()
            );
            
            AiMessage aiMessage = response.content();
            messages.add(aiMessage);
            
            // 检查是否有工具调用请求
            if (!aiMessage.hasToolExecutionRequests()) {
                // 没有工具调用，返回最终回复
                log.info("✅ Agent completed after {} iterations", i + 1);
                return aiMessage.text();
            }
            
            // 执行工具调用
            List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();
            log.info("🔧 Executing {} tool(s)", toolRequests.size());
            
            for (ToolExecutionRequest request : toolRequests) {
                String toolName = request.name();
                String toolArgs = request.arguments();
                
                log.info("🔧 Tool: {} | Args: {}", toolName, toolArgs);
                
                // 执行工具
                String toolResult = executeToolByName(toolName, toolArgs);
                
                // 将工具结果添加到对话中
                messages.add(ToolExecutionResultMessage.from(request, toolResult));
            }
        }
        
        log.warn("⚠️ Agent reached max iterations");
        return "抱歉，处理过程较复杂，请尝试简化您的问题。";
    }

    /**
     * 根据工具名称执行对应方法
     */
    private String executeToolByName(String toolName, String argsJson) {
        try {
            // 解析参数
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            
            return switch (toolName) {
                case "searchMovies" -> {
                    String keyword = (String) args.get("keyword");
                    yield aiMovieTools.searchMovies(keyword);
                }
                case "getMovieDetail" -> {
                    Object filmIdObj = args.get("filmId");
                    Long filmId = filmIdObj instanceof Number ? ((Number) filmIdObj).longValue() : Long.parseLong(filmIdObj.toString());
                    yield aiMovieTools.getMovieDetail(filmId);
                }
                case "getRecommendedMovies" -> {
                    Object countObj = args.getOrDefault("count", 5);
                    int count = countObj instanceof Number ? ((Number) countObj).intValue() : Integer.parseInt(countObj.toString());
                    yield aiMovieTools.getRecommendedMovies(count);
                }
                case "getHotMovies" -> {
                    Object countObj = args.getOrDefault("count", 10);
                    int count = countObj instanceof Number ? ((Number) countObj).intValue() : Integer.parseInt(countObj.toString());
                    yield aiMovieTools.getHotMovies(count);
                }
                case "ragSearch" -> {
                    String query = (String) args.get("query");
                    yield aiMovieTools.ragSearch(query);
                }
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("执行工具失败: {} | {}", toolName, e.getMessage());
            return "工具执行出错: " + e.getMessage();
        }
    }

    /**
     * 降级：普通对话（无工具）
     */
    private String fallbackChat(ChatRequestDTO dto) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(AGENT_SYSTEM_PROMPT));
        messages.add(UserMessage.from(dto.getPrompt()));
        
        Response<AiMessage> response = chatLanguageModel.generate(messages);
        return response.content().text();
    }
}
