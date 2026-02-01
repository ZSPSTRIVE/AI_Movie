package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import com.jelly.cinema.ai.service.AgentChatService;
import com.jelly.cinema.ai.tools.PythonRagClient;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI Agent 对话控制器
 * 支持自动工具调用的智能对话接口
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Tag(name = "AI Agent 对话")
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AgentChatController {

    private final AgentChatService agentChatService;
    private final PythonRagClient pythonRagClient;

    @Operation(summary = "Agent 同步对话（带工具调用）")
    @PostMapping("/chat")
    public R<String> chat(@RequestBody ChatRequestDTO dto) {
        return R.ok(agentChatService.chat(dto));
    }

    @Operation(summary = "Agent 流式对话（带工具调用）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequestDTO dto) {
        return agentChatService.chatStream(dto)
                .map(token -> "data: " + token + "\n\n");
    }

    @Operation(summary = "Agent 流式对话 - GET方式")
    @GetMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completions(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "false") Boolean enableRag) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setPrompt(prompt);
        dto.setEnableRag(enableRag);
        return agentChatService.chatStream(dto)
                .map(token -> "data: " + token + "\n\n");
    }

    @Operation(summary = "同步电影数据到向量库")
    @PostMapping("/sync/films")
    public R<String> syncFilms() {
        String result = pythonRagClient.syncFilms();
        return R.ok(result);
    }

    @Operation(summary = "检查 RAG 服务健康状态")
    @GetMapping("/health/rag")
    public R<Boolean> checkRagHealth() {
        return R.ok(pythonRagClient.isHealthy());
    }
}
