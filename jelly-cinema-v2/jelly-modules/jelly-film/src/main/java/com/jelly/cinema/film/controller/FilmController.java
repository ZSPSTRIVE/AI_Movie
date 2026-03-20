package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.dto.FilmQueryDTO;
import com.jelly.cinema.film.domain.vo.FilmVO;
import com.jelly.cinema.film.service.FilmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 电影控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "电影管理")
@RestController
@RequestMapping("/film")
@RequiredArgsConstructor
public class FilmController {

    private final FilmService filmService;

    @Operation(summary = "分页查询电影列表")
    @GetMapping("/list")
    public R<PageResult<FilmVO>> list(FilmQueryDTO dto) {
        return R.ok(filmService.list(dto));
    }

    @Operation(summary = "获取电影详情")
    @GetMapping("/detail/{id}")
    public R<FilmVO> getDetail(@PathVariable Long id) {
        return R.ok(filmService.getDetail(id));
    }

    @Operation(summary = "搜索电影")
    @GetMapping("/search")
    public R<List<FilmVO>> search(
            @Parameter(description = "搜索关键词") @RequestParam(value = "keyword") String keyword) {
        return R.ok(filmService.search(keyword));
    }

    @Operation(summary = "获取推荐电影")
    @GetMapping("/recommend/feed")
    public R<List<FilmVO>> getRecommend(
            @Parameter(description = "数量") @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(filmService.getRecommend(size));
    }

    @Operation(summary = "获取热门榜单")
    @GetMapping("/recommend/hot")
    public R<List<FilmVO>> getHotRank(
            @Parameter(description = "数量") @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return R.ok(filmService.getHotRank(size));
    }

    @Operation(summary = "增加播放量")
    @PostMapping("/play/{id}")
    public R<Void> play(@PathVariable Long id) {
        filmService.incrementPlayCount(id);
        return R.ok();
    }

    @Operation(summary = "将电影库同步到 Python RAG")
    @PostMapping("/rag/sync")
    public R<Integer> syncFilmsToRag(
            @Parameter(description = "同步数量") @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok("同步完成", filmService.syncFilmsToRag(limit));
    }

    @Operation(summary = "后台触发电影库同步到 Python RAG")
    @PostMapping("/rag/sync/start")
    public R<Map<String, Object>> startSyncFilmsToRag(
            @Parameter(description = "同步数量") @RequestParam(value = "limit", required = false) Integer limit) {
        return R.ok(filmService.startSyncFilmsToRag(limit));
    }

    @Operation(summary = "获取电影库同步到 Python RAG 的任务状态")
    @GetMapping("/rag/sync/status")
    public R<Map<String, Object>> getSyncFilmsToRagStatus() {
        return R.ok(filmService.getSyncFilmsToRagStatus());
    }
}
