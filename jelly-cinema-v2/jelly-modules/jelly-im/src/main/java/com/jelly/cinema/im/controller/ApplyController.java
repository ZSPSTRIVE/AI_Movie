package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.im.domain.dto.ApplyHandleDTO;
import com.jelly.cinema.im.domain.dto.FriendApplyDTO;
import com.jelly.cinema.im.domain.dto.GroupApplyDTO;
import com.jelly.cinema.im.domain.vo.ApplyRecordVO;
import com.jelly.cinema.im.service.ApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 申请管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "好友/群申请")
@RestController
@RequestMapping("/im/apply")
@RequiredArgsConstructor
public class ApplyController {

    private final ApplyService applyService;

    @Operation(summary = "发起好友申请")
    @PostMapping("/friend")
    public R<Void> applyFriend(@Validated @RequestBody FriendApplyDTO dto) {
        applyService.applyFriend(dto);
        return R.ok();
    }

    @Operation(summary = "发起入群申请")
    @PostMapping("/group")
    public R<Void> applyGroup(@Validated @RequestBody GroupApplyDTO dto) {
        applyService.applyGroup(dto);
        return R.ok();
    }

    @Operation(summary = "获取申请列表")
    @GetMapping("/list")
    public R<List<ApplyRecordVO>> getApplyList(
            @RequestParam(required = false) Integer type) {
        return R.ok(applyService.getApplyList(type));
    }

    @Operation(summary = "处理申请")
    @PostMapping("/handle")
    public R<Void> handleApply(@Validated @RequestBody ApplyHandleDTO dto) {
        applyService.handleApply(dto);
        return R.ok();
    }

    @Operation(summary = "获取未读申请数量")
    @GetMapping("/unread-count")
    public R<Integer> getUnreadCount() {
        return R.ok(applyService.getUnreadCount());
    }
}
