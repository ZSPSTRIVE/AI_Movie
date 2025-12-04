package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.recommend.RecommendService;
import com.jelly.cinema.film.search.FilmSearchService;
import com.jelly.cinema.film.service.FilmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推荐与搜索控制器
 * 
 * @author Jelly Cinema
 */
@Tag(name = "推荐与搜索")
@RestController
@RequestMapping("/film")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;
    private final FilmSearchService filmSearchService;
    private final FilmService filmService;

    // ==================== 搜索接口 ====================

    @Operation(summary = "全文搜索电影（高级版）")
    @GetMapping("/search/advanced")
    public R<FilmSearchService.SearchResult> search(
            @Parameter(description = "关键词") @RequestParam String keyword,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        
        FilmSearchService.SearchResult result = filmSearchService.search(keyword, categoryId, page, size);
        return R.ok(result);
    }

    @Operation(summary = "搜索建议")
    @GetMapping("/search/suggest")
    public R<List<String>> suggest(
            @Parameter(description = "关键词前缀") @RequestParam String prefix,
            @Parameter(description = "建议数量") @RequestParam(defaultValue = "10") int size) {
        
        List<String> suggestions = filmSearchService.suggest(prefix, size);
        return R.ok(suggestions);
    }

    @Operation(summary = "按标签搜索")
    @GetMapping("/search/tags")
    public R<List<FilmVO>> searchByTags(
            @Parameter(description = "标签列表") @RequestParam List<String> tags,
            @Parameter(description = "数量") @RequestParam(defaultValue = "20") int size) {
        
        List<Long> filmIds = filmSearchService.searchByTags(tags, size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    // ==================== 推荐接口 ====================

    @Operation(summary = "个性化推荐")
    @GetMapping("/recommend")
    public R<List<FilmVO>> recommend(
            @Parameter(description = "数量") @RequestParam(defaultValue = "20") int size) {
        
        Long userId = LoginHelper.getUserId();
        List<Long> filmIds = recommendService.hybridRecommend(userId, size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    @Operation(summary = "基于标签推荐")
    @GetMapping("/recommend/tags")
    public R<List<FilmVO>> recommendByTags(
            @Parameter(description = "数量") @RequestParam(defaultValue = "20") int size) {
        
        Long userId = LoginHelper.getUserId();
        List<Long> filmIds = recommendService.recommendByTags(userId, size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    @Operation(summary = "协同过滤推荐")
    @GetMapping("/recommend/cf")
    public R<List<FilmVO>> recommendByCF(
            @Parameter(description = "数量") @RequestParam(defaultValue = "20") int size) {
        
        Long userId = LoginHelper.getUserId();
        List<Long> filmIds = recommendService.recommendByUserCF(userId, size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    @Operation(summary = "热门推荐（高级版）")
    @GetMapping("/recommend/hot/advanced")
    public R<List<FilmVO>> hotRecommend(
            @Parameter(description = "数量") @RequestParam(defaultValue = "20") int size) {
        
        List<Long> filmIds = recommendService.getHotRecommend(size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    @Operation(summary = "相似电影推荐")
    @GetMapping("/recommend/similar/{filmId}")
    public R<List<FilmVO>> similarFilms(
            @Parameter(description = "电影ID") @PathVariable Long filmId,
            @Parameter(description = "数量") @RequestParam(defaultValue = "10") int size) {
        
        List<Long> filmIds = recommendService.getSimilarFilms(filmId, size);
        List<FilmVO> films = filmService.getFilmsByIds(filmIds);
        return R.ok(films);
    }

    @Operation(summary = "清除推荐缓存")
    @PostMapping("/recommend/cache/clear")
    public R<Void> clearRecommendCache() {
        Long userId = LoginHelper.getUserId();
        recommendService.clearUserRecommendCache(userId);
        return R.ok();
    }
}
