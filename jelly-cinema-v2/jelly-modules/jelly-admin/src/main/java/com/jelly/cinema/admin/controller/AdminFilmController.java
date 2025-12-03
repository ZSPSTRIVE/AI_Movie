package com.jelly.cinema.admin.controller;

import com.jelly.cinema.admin.domain.dto.FilmCreateDTO;
import com.jelly.cinema.admin.domain.entity.Film;
import com.jelly.cinema.admin.service.AdminFilmService;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 电影管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "电影管理")
@RestController
@RequestMapping("/admin/film")
@RequiredArgsConstructor
public class AdminFilmController {

    private final AdminFilmService filmService;

    @Operation(summary = "分页查询电影")
    @GetMapping("/list")
    public R<PageResult<Film>> list(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId) {
        return R.ok(filmService.list(query, keyword, categoryId));
    }

    @Operation(summary = "获取电影详情")
    @GetMapping("/{id}")
    public R<Film> getById(@PathVariable Long id) {
        return R.ok(filmService.getById(id));
    }

    @Operation(summary = "创建/更新电影")
    @PostMapping("/save")
    public R<Long> save(@Valid @RequestBody FilmCreateDTO dto) {
        return R.ok(filmService.saveOrUpdate(dto));
    }

    @Operation(summary = "上架/下架")
    @PostMapping("/status/{id}")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        filmService.updateStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "删除电影")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        filmService.delete(id);
        return R.ok();
    }
}
