package com.jelly.cinema.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.SensitiveWord;
import com.jelly.cinema.admin.service.SensitiveWordService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 敏感词管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "敏感词管理")
@RestController
@RequestMapping("/admin/sensitive")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @Operation(summary = "分页查询敏感词")
    @GetMapping("/list")
    public R<Page<SensitiveWord>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type) {
        return R.ok(sensitiveWordService.page(pageNum, pageSize, keyword, type));
    }

    @Operation(summary = "添加敏感词")
    @PostMapping("/add")
    public R<Void> add(@RequestBody SensitiveWord word) {
        sensitiveWordService.add(word);
        return R.ok();
    }

    @Operation(summary = "批量导入敏感词")
    @PostMapping("/import")
    public R<Integer> batchImport(@RequestBody BatchImportDTO dto) {
        // 按逗号、换行符分割
        List<String> words = Arrays.stream(dto.getWords().split("[,，\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        
        int count = sensitiveWordService.batchAdd(words, dto.getType(), dto.getStrategy());
        return R.ok("成功导入 " + count + " 个敏感词", count);
    }

    @Operation(summary = "删除敏感词")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Integer id) {
        sensitiveWordService.delete(id);
        return R.ok();
    }

    @Operation(summary = "启用/禁用敏感词")
    @PostMapping("/status")
    public R<Void> updateStatus(@RequestBody UpdateStatusDTO dto) {
        sensitiveWordService.updateStatus(dto.getId(), dto.getStatus());
        return R.ok();
    }

    @Operation(summary = "刷新敏感词缓存")
    @PostMapping("/refresh")
    public R<Void> refresh() {
        sensitiveWordService.refreshCache();
        return R.ok();
    }

    @Data
    public static class BatchImportDTO {
        private String words;
        private Integer type;
        private Integer strategy;
    }

    @Data
    public static class UpdateStatusDTO {
        private Integer id;
        private Integer status;
    }
}
