package com.jelly.cinema.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.AdminLog;
import com.jelly.cinema.admin.domain.vo.GroupAuditVO;
import com.jelly.cinema.admin.mapper.AdminLogMapper;
import com.jelly.cinema.admin.service.GroupAuditService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 群组审计控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "群组审计")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class GroupAuditController {

    private final GroupAuditService groupAuditService;
    private final AdminLogMapper adminLogMapper;

    @Operation(summary = "分页查询群组列表")
    @GetMapping("/groups")
    public R<Page<GroupAuditVO>> listGroups(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return R.ok(groupAuditService.pageGroups(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取群聊历史消息（审计）")
    @GetMapping("/group/{groupId}/messages")
    public R<Page<?>> getGroupMessages(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        return R.ok(groupAuditService.getGroupMessages(groupId, pageNum, pageSize));
    }

    @Operation(summary = "强制解散群组")
    @PostMapping("/group/dismiss")
    public R<Void> dismissGroup(@RequestBody DismissDTO dto) {
        groupAuditService.dismissGroup(dto.getGroupId(), dto.getReason());

        // 记录操作日志
        AdminLog log = new AdminLog();
        log.setAdminId(LoginHelper.getUserId());
        log.setModule(AdminLog.MODULE_GROUP);
        log.setAction(AdminLog.ACTION_DISMISS);
        log.setTargetId(dto.getGroupId());
        log.setTargetType("group");
        log.setDetail("原因: " + dto.getReason());
        adminLogMapper.insert(log);

        return R.ok();
    }

    @Data
    public static class DismissDTO {
        private Long groupId;
        private String reason;
    }
}
