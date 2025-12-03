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
        return chatService.chatStream(dto)
                .map(token -> "data: " + token + "\n\n");
    }

    @Operation(summary = "流式对话 - GET方式 (SSE)")
    @GetMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> completions(
            @RequestParam String prompt,
            @RequestParam(required = false, defaultValue = "false") Boolean enableRag) {
        ChatRequestDTO dto = new ChatRequestDTO();
        dto.setPrompt(prompt);
        dto.setEnableRag(enableRag);
        return chatService.chatStream(dto)
                .map(token -> "data: " + token + "\n\n");
    }
}
