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
}
