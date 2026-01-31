package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.entity.TvboxSource;
import com.jelly.cinema.film.mapper.TvboxSourceMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * TVBox采集源管理控制器（管理端）
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Slf4j
@Tag(name = "采集源配置管理")
@RestController
@RequestMapping("/admin/tvbox-source")
@RequiredArgsConstructor
public class TvboxSourceController {

    private final TvboxSourceMapper tvboxSourceMapper;

    @Operation(summary = "获取采集源列表")
    @GetMapping("/list")
    public R<List<TvboxSource>> list() {
        try {
            List<TvboxSource> sources = tvboxSourceMapper.selectList(null);
            return R.ok(sources != null ? sources : new ArrayList<>());
        } catch (Exception e) {
            log.error("获取采集源列表失败: {}", e.getMessage(), e);
            // 返回空列表而不是抛异常
            return R.ok(new ArrayList<>());
        }
    }

    @Operation(summary = "获取启用的采集源列表")
    @GetMapping("/enabled")
    public R<List<TvboxSource>> getEnabledList() {
        try {
            return R.ok(tvboxSourceMapper.selectEnabledSources());
        } catch (Exception e) {
            log.error("获取启用采集源失败: {}", e.getMessage(), e);
            return R.ok(new ArrayList<>());
        }
    }

    @Operation(summary = "获取采集源详情")
    @GetMapping("/{id}")
    public R<TvboxSource> getDetail(@PathVariable Long id) {
        try {
            return R.ok(tvboxSourceMapper.selectById(id));
        } catch (Exception e) {
            log.error("获取采集源详情失败: {}", e.getMessage(), e);
            return R.fail("获取详情失败");
        }
    }

    @Operation(summary = "新增采集源")
    @PostMapping
    public R<Void> add(@RequestBody TvboxSource source) {
        try {
            tvboxSourceMapper.insert(source);
            return R.ok();
        } catch (Exception e) {
            log.error("新增采集源失败: {}", e.getMessage(), e);
            return R.fail("新增失败: " + e.getMessage());
        }
    }

    @Operation(summary = "更新采集源")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody TvboxSource source) {
        try {
            source.setId(id);
            tvboxSourceMapper.updateById(source);
            return R.ok();
        } catch (Exception e) {
            log.error("更新采集源失败: {}", e.getMessage(), e);
            return R.fail("更新失败");
        }
    }

    @Operation(summary = "删除采集源")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        try {
            tvboxSourceMapper.deleteById(id);
            return R.ok();
        } catch (Exception e) {
            log.error("删除采集源失败: {}", e.getMessage(), e);
            return R.fail("删除失败");
        }
    }

    @Operation(summary = "启用/禁用采集源")
    @PutMapping("/{id}/toggle")
    public R<Void> toggle(@PathVariable Long id) {
        try {
            TvboxSource source = tvboxSourceMapper.selectById(id);
            if (source != null) {
                source.setEnabled(source.getEnabled() == 1 ? 0 : 1);
                tvboxSourceMapper.updateById(source);
            }
            return R.ok();
        } catch (Exception e) {
            log.error("切换状态失败: {}", e.getMessage(), e);
            return R.fail("操作失败");
        }
    }

    @Operation(summary = "更新优先级")
    @PutMapping("/{id}/priority")
    public R<Void> updatePriority(
            @PathVariable Long id,
            @Parameter(description = "优先级") @RequestParam Integer priority) {
        try {
            TvboxSource source = new TvboxSource();
            source.setId(id);
            source.setPriority(priority);
            tvboxSourceMapper.updateById(source);
            return R.ok();
        } catch (Exception e) {
            log.error("更新优先级失败: {}", e.getMessage(), e);
            return R.fail("操作失败");
        }
    }
}
