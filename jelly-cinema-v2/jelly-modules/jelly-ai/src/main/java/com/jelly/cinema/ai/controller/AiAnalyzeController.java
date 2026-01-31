package com.jelly.cinema.ai.controller;

import com.jelly.cinema.ai.service.AiModelService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI分析控制器 - 提供跨模块调用的REST API
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Tag(name = "AI分析服务")
@RestController
@RequestMapping("/ai/analyze")
@RequiredArgsConstructor
public class AiAnalyzeController {

    private final AiModelService aiModelService;

    @Operation(summary = "分析单部电影")
    @PostMapping("/film")
    public R<Map<String, Object>> analyzeFilm(@RequestBody Map<String, Object> film) {
        String title = (String) film.get("title");
        String description = (String) film.get("description");
        Integer year = film.get("year") instanceof Integer ? (Integer) film.get("year") : null;
        String actors = (String) film.get("actors");
        String director = (String) film.get("director");
        String region = (String) film.get("region");
        
        Map<String, Object> result = aiModelService.analyzeFilm(title, description, year, actors, director, region);
        return R.ok(result);
    }

    @Operation(summary = "批量分析并排序电影")
    @PostMapping("/batch-sort")
    public R<List<Map<String, Object>>> batchAnalyzeAndSort(@RequestBody List<Map<String, Object>> films) {
        log.info("收到批量分析请求，电影数量: {}", films.size());
        List<Map<String, Object>> result = aiModelService.batchAnalyzeAndSort(films);
        return R.ok(result);
    }

    @Operation(summary = "智能筛选推荐内容")
    @PostMapping("/filter")
    public R<List<Map<String, Object>>> intelligentFilter(
            @RequestBody List<Map<String, Object>> films,
            @RequestParam(defaultValue = "18") int targetCount) {
        List<Map<String, Object>> result = aiModelService.intelligentFilter(films, targetCount);
        return R.ok(result);
    }
}
