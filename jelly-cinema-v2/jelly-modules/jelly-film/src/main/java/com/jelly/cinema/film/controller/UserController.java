package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.vo.FavoriteVO;
import com.jelly.cinema.film.domain.vo.WatchHistoryVO;
import com.jelly.cinema.film.service.UserFavoriteService;
import com.jelly.cinema.film.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户相关控制器（收藏、观看历史）
 *
 * @author Jelly Cinema
 */
@Tag(name = "用户收藏与历史")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserFavoriteService favoriteService;
    private final WatchHistoryService historyService;

    // ==================== 收藏相关 ====================

    @Operation(summary = "获取我的收藏列表")
    @GetMapping("/favorites")
    public R<PageResult<FavoriteVO>> listFavorites(PageQuery query) {
        return R.ok(favoriteService.listMyFavorites(query));
    }

    @Operation(summary = "添加收藏")
    @PostMapping("/favorites/{filmId}")
    public R<Void> addFavorite(@PathVariable Long filmId) {
        favoriteService.addFavorite(filmId);
        return R.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{filmId}")
    public R<Void> removeFavorite(@PathVariable Long filmId) {
        favoriteService.removeFavorite(filmId);
        return R.ok();
    }

    @Operation(summary = "检查是否已收藏")
    @GetMapping("/favorites/check/{filmId}")
    public R<Boolean> checkFavorite(@PathVariable Long filmId) {
        return R.ok(favoriteService.isFavorite(filmId));
    }

    // ==================== 观看历史相关 ====================

    @Operation(summary = "获取观看历史")
    @GetMapping("/history")
    public R<PageResult<WatchHistoryVO>> listHistory(PageQuery query) {
        return R.ok(historyService.listMyHistory(query));
    }

    @Operation(summary = "记录观看进度")
    @PostMapping("/history")
    public R<Void> recordProgress(
            @Parameter(description = "电影ID") @RequestParam Long filmId,
            @Parameter(description = "观看进度(0-100)") @RequestParam Integer progress) {
        historyService.recordProgress(filmId, progress);
        return R.ok();
    }

    @Operation(summary = "删除单条观看记录")
    @DeleteMapping("/history/{id}")
    public R<Void> deleteHistory(@PathVariable Long id) {
        historyService.deleteHistory(id);
        return R.ok();
    }

    @Operation(summary = "清空观看历史")
    @DeleteMapping("/history/clear")
    public R<Void> clearHistory() {
        historyService.clearHistory();
        return R.ok();
    }
}
