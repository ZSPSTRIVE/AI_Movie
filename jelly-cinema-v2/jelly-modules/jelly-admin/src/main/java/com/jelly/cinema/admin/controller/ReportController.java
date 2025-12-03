package com.jelly.cinema.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.ReportVO;
import com.jelly.cinema.admin.service.ReportService;
import com.jelly.cinema.common.core.domain.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 举报管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "举报管理")
@RestController
@RequestMapping("/admin/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "分页查询举报列表")
    @GetMapping("/list")
    public R<Page<ReportVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer targetType) {
        return R.ok(reportService.page(pageNum, pageSize, status, targetType));
    }

    @Operation(summary = "获取举报详情")
    @GetMapping("/{id}")
    public R<ReportVO> getDetail(@PathVariable Long id) {
        return R.ok(reportService.getDetail(id));
    }

    @Operation(summary = "处理举报")
    @PostMapping("/handle")
    public R<Void> handle(@RequestBody HandleDTO dto) {
        reportService.handle(dto.getId(), dto.getAction(), dto.getFeedback());
        return R.ok();
    }

    @Operation(summary = "获取待处理数量")
    @GetMapping("/pending-count")
    public R<Integer> getPendingCount() {
        return R.ok(reportService.getPendingCount());
    }

    @Data
    public static class HandleDTO {
        private Long id;
        private Integer action; // 1-忽略, 2-警告, 3-封禁
        private String feedback;
    }
}
