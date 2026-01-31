package com.jelly.cinema.film.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.film.domain.entity.HomepageConfigVersion;
import com.jelly.cinema.film.service.DraftGeneratorService;
import com.jelly.cinema.film.service.HomepageConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 首页配置运营控制器（商业化）
 *
 * @author Jelly Cinema
 * @since 2026
 */
@Tag(name = "首页配置运营（商业化）")
@RestController
@RequestMapping("/admin/homepage/config")
@RequiredArgsConstructor
public class HomepageConfigController {

    private final HomepageConfigService configService;
    private final DraftGeneratorService draftService;

    @Operation(summary = "生成配置草案")
    @PostMapping("/generate-draft")
    public R<HomepageConfigVersion> generateDraft(
            @RequestParam String category,
            @RequestParam(defaultValue = "admin") String createdBy) {
        return R.ok(draftService.generateDraft(category, createdBy));
    }

    @Operation(summary = "发布配置版本")
    @PostMapping("/publish/{id}")
    public R<Void> publish(@PathVariable Long id) {
        configService.publish(id);
        return R.ok();
    }

    @Operation(summary = "回滚配置")
    @PostMapping("/rollback/{id}")
    public R<Void> rollback(@PathVariable Long id) {
        configService.rollback(id);
        return R.ok();
    }
}
