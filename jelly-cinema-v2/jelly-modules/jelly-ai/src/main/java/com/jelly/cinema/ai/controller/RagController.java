package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.service.RagService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 知识库控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "RAG 知识库")
@RestController
@RequestMapping("/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @Operation(summary = "上传文档进行 RAG 索引")
    @PostMapping("/upload")
    public R<Long> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(ragService.uploadDocument(file));
    }

    @Operation(summary = "检索相关文档")
    @GetMapping("/search")
    public R<String> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") Integer topK) {
        return R.ok(ragService.retrieve(query, topK));
    }
}
