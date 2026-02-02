package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.domain.dto.ChatRequestDTO;
import com.jelly.cinema.ai.service.ChatService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI 对话控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "AI 对话")
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "同步对话")
    @PostMapping
    public R<String> chat(@RequestBody ChatRequestDTO dto) {
        return R.ok(chatService.chat(dto));
    }

    @Operation(summary = "流式对话 (SSE)")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequestDTO dto) {
        // Spring WebFlux 在 TEXT_EVENT_STREAM 下会自动按 SSE 格式包装 data: ...\n\n
        // 这里直接返回 token 流，避免出现 data:data: 的重复前缀
        return chatService.chatStream(dto);
    }

    @Operation(summary = "流式对话 - GET方式 (SSE)")
    @GetMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completions(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "false") Boolean enableRag) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setPrompt(prompt);
        dto.setEnableRag(enableRag);
        
        // 批量缓冲 - 每5个字符或遇到特殊字符时发送，减少SSE事件数量
        final int BATCH_SIZE = 5;
        final StringBuilder buffer = new StringBuilder();
        
        return chatService.chatStream(dto)
                .concatMap(token -> {
                    buffer.append(token);
                    // 当缓冲达到批量大小，或遇到换行/句号/问号/叹号时发送
                    if (buffer.length() >= BATCH_SIZE || 
                        token.contains("\n") || token.contains("。") || 
                        token.contains("？") || token.contains("！") ||
                        token.contains(".") || token.contains("?") || token.contains("!")) {
                        String chunk = buffer.toString();
                        buffer.setLength(0);
                        return Flux.just(chunk);
                    }
                    return Flux.empty();
                })
                .concatWith(Flux.defer(() -> {
                    // 发送剩余的缓冲内容
                    if (buffer.length() > 0) {
                        return Flux.just(buffer.toString());
                    }
                    return Flux.empty();
                }));
    }
}
