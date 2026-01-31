package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.service.FilmRecommendService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI影片推荐控制器
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Tag(name = "AI影片推荐")
@RestController
@RequestMapping("/ai/recommend")
@RequiredArgsConstructor
public class FilmRecommendController {

    private final FilmRecommendService filmRecommendService;

    @Operation(summary = "批量分析影片", description = "使用AI分析影片列表，生成推荐评分和推荐理由")
    @PostMapping("/analyze")
    public R<List<Map<String, Object>>> analyzeFilms(@RequestBody List<Map<String, Object>> films) {
        return R.ok(filmRecommendService.analyzeFilms(films));
    }

    @Operation(summary = "获取AI精选推荐")
    @GetMapping("/best")
    public R<List<Map<String, Object>>> getBestRecommendations(
            @Parameter(description = "分类") @RequestParam(defaultValue = "movie") String category,
            @Parameter(description = "数量") @RequestParam(defaultValue = "6") Integer limit) {
        return R.ok(filmRecommendService.getBestRecommendations(category, limit));
    }

    @Operation(summary = "生成单个影片推荐理由")
    @PostMapping("/reason")
    public R<String> generateReason(@RequestBody Map<String, Object> filmInfo) {
        return R.ok(filmRecommendService.generateRecommendReason(filmInfo));
    }
}
