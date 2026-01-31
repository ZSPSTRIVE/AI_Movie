package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.entity.HomepageContent;
import com.jelly.cinema.film.domain.vo.HomepageContentVO;
import com.jelly.cinema.film.service.HomepageContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 首页内容管理控制器（管理端）
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Tag(name = "首页内容管理")
@RestController
@RequestMapping("/admin/homepage")
@RequiredArgsConstructor
public class HomepageContentController {

    private final HomepageContentService homepageContentService;

    @Operation(summary = "分页查询首页内容列表")
    @GetMapping("/list")
    public R<PageResult<HomepageContentVO>> list(
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "板块类型") @RequestParam(required = false) String sectionType,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return R.ok(homepageContentService.pageList(contentType, sectionType, pageNum, pageSize));
        } catch (Exception e) {
            // 表不存在或其他错误时返回空结果
            return R.ok(new PageResult<>(new java.util.ArrayList<>(), 0L));
        }
    }

    @Operation(summary = "获取内容详情")
    @GetMapping("/{id}")
    public R<HomepageContent> getDetail(@PathVariable Long id) {
        return R.ok(homepageContentService.getById(id));
    }

    @Operation(summary = "新增首页内容")
    @PostMapping
    public R<Void> add(@RequestBody HomepageContent content) {
        homepageContentService.save(content);
        return R.ok();
    }

    @Operation(summary = "更新首页内容")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody HomepageContent content) {
        content.setId(id);
        homepageContentService.updateById(content);
        return R.ok();
    }

    @Operation(summary = "删除首页内容")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        homepageContentService.removeById(id);
        return R.ok();
    }

    @Operation(summary = "更新排序")
    @PutMapping("/{id}/sort")
    public R<Void> updateSort(
            @PathVariable Long id,
            @Parameter(description = "排序值") @RequestParam Integer sortOrder) {
        homepageContentService.updateSortOrder(id, sortOrder);
        return R.ok();
    }

    @Operation(summary = "切换状态")
    @PutMapping("/{id}/toggle-status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        homepageContentService.toggleStatus(id);
        return R.ok();
    }

    @Operation(summary = "刷新内容（从TVBox采集）")
    @PostMapping("/refresh")
    public R<String> refresh() {
        homepageContentService.refreshContent();
        return R.ok("刷新任务已启动");
    }

    @Operation(summary = "AI智能排序")
    @PostMapping("/ai-sort")
    public R<String> aiSort() {
        homepageContentService.aiSort();
        return R.ok("AI排序任务已启动");
    }

    // ========== 前端首页接口 ==========

    @Operation(summary = "获取首页推荐列表（前端使用）")
    @GetMapping("/recommend")
    public R<List<HomepageContentVO>> getRecommend(
            @Parameter(description = "数量") @RequestParam(defaultValue = "18") Integer limit) {
        return R.ok(homepageContentService.getRecommendList(limit));
    }

    @Operation(summary = "获取热门列表（前端使用）")
    @GetMapping("/hot")
    public R<List<HomepageContentVO>> getHot(
            @Parameter(description = "数量") @RequestParam(defaultValue = "10") Integer limit) {
        return R.ok(homepageContentService.getHotList(limit));
    }

    @Operation(summary = "获取电影列表（前端使用）")
    @GetMapping("/movies")
    public R<List<HomepageContentVO>> getMovies(
            @Parameter(description = "数量") @RequestParam(defaultValue = "18") Integer limit) {
        return R.ok(homepageContentService.getMovieList(limit));
    }

    @Operation(summary = "获取电视剧列表（前端使用）")
    @GetMapping("/tv-series")
    public R<List<HomepageContentVO>> getTvSeries(
            @Parameter(description = "数量") @RequestParam(defaultValue = "12") Integer limit) {
        return R.ok(homepageContentService.getTvSeriesList(limit));
    }

    @Operation(summary = "获取AI精选列表（前端使用）")
    @GetMapping("/ai-best")
    public R<List<HomepageContentVO>> getAiBest(
            @Parameter(description = "数量") @RequestParam(defaultValue = "6") Integer limit) {
        return R.ok(homepageContentService.getAiBestList(limit));
    }

    @Operation(summary = "获取新片列表（前端使用）")
    @GetMapping("/new")
    public R<List<HomepageContentVO>> getNew(
            @Parameter(description = "数量") @RequestParam(defaultValue = "12") Integer limit) {
        return R.ok(homepageContentService.getNewList(limit));
    }

    @Operation(summary = "获取趋势/热门话题列表（前端使用）")
    @GetMapping("/trending")
    public R<List<HomepageContentVO>> getTrending(
            @Parameter(description = "数量") @RequestParam(defaultValue = "8") Integer limit) {
        return R.ok(homepageContentService.getTrendingList(limit));
    }

    @Operation(summary = "获取分板块首页内容（前端使用）")
    @GetMapping("/sections")
    public R<java.util.Map<String, List<HomepageContentVO>>> getSections() {
        return R.ok(homepageContentService.getSectionedContent());
    }

    @Operation(summary = "标记/取消AI精选")
    @PutMapping("/{id}/mark-best")
    public R<Void> markBest(
            @PathVariable Long id,
            @Parameter(description = "是否AI精选") @RequestParam(defaultValue = "true") Boolean isBest) {
        homepageContentService.markAsBest(id, isBest);
        return R.ok();
    }
}
