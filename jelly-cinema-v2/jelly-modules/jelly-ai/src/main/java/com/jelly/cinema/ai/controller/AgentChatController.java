package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import com.jelly.cinema.ai.service.AgentChatService;
import com.jelly.cinema.ai.tools.PythonRagClient;
import com.jelly.cinema.common.api.feign.RemoteFilmService;
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
    private final RemoteFilmService remoteFilmService;

    @Operation(summary = "Agent 同步对话（带工具调用）")
    @PostMapping("/chat")
    public R<String> chat(@RequestBody ChatRequestDTO dto) {
        return R.ok(agentChatService.chat(dto));
    }

    @Operation(summary = "Agent 流式对话（带工具调用）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequestDTO dto) {
        // Spring WebFlux 在 TEXT_EVENT_STREAM 下会自动按 SSE 格式包装 data: ...\n\n
        // 这里直接返回 token 流，避免出现 data:data: 的重复前缀
        return agentChatService.chatStream(dto);
    }

    @Operation(summary = "Agent 流式对话 - GET方式")
    @GetMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completions(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "false") Boolean enableRag) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setPrompt(prompt);
        dto.setEnableRag(enableRag);
        return agentChatService.chatStream(dto);
    }

    @Operation(summary = "重建 Python RAG 索引")
    @PostMapping("/sync/films")
    public R<String> syncFilms() {
        R<Integer> result = remoteFilmService.syncFilmsToRag(null);
        if (result == null || !result.isSuccess()) {
            return R.fail(result == null ? "电影服务暂时不可用" : result.getMsg());
        }
        Integer count = result.getData() == null ? 0 : result.getData();
        return R.ok("已按 MySQL -> Python RAG 链路完成同步，共 " + count + " 条", null);
    }

    @Operation(summary = "检查 RAG 服务健康状态")
    @GetMapping("/health/rag")
    public R<Boolean> checkRagHealth() {
        return R.ok(pythonRagClient.isHealthy());
    }
}
