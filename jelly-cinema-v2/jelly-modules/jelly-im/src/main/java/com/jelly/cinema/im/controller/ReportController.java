package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.dto.ReportDTO;
import com.jelly.cinema.im.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 举报控制器 (用户端)
 *
 * @author Jelly Cinema
 */
@Tag(name = "举报")
@RestController
@RequestMapping("/im/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "提交举报")
    @PostMapping
    public R<String> submit(@Validated @RequestBody ReportDTO dto) {
        Long userId = LoginHelper.getUserId();
        reportService.submit(userId, dto);
        return R.ok("举报已提交");
    }
}
