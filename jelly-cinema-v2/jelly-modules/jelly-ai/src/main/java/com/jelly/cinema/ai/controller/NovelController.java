package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.domain.dto.NovelChapterDTO;
import com.jelly.cinema.ai.domain.dto.NovelOutlineDTO;
import com.jelly.cinema.ai.service.NovelService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI 小说生成控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "AI 小说生成")
@RestController
@RequestMapping("/ai/novel")
@RequiredArgsConstructor
public class NovelController {

    private final NovelService novelService;

    @Operation(summary = "生成小说大纲")
    @PostMapping("/generate-outline")
    public R<String> generateOutline(@Valid @RequestBody NovelOutlineDTO dto) {
        return R.ok(novelService.generateOutline(dto));
    }

    @Operation(summary = "生成章节内容 (流式)")
    @PostMapping(value = "/generate-chapter", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateChapter(@Valid @RequestBody NovelChapterDTO dto) {
        return novelService.generateChapter(dto)
                .map(token -> "data: " + token + "\n\n");
    }
}
