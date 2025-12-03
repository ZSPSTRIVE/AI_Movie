package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.im.domain.vo.GroupMemberVO;
import com.jelly.cinema.im.domain.vo.GroupVO;
import com.jelly.cinema.im.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群组管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "群组管理")
@RestController
@RequestMapping("/im/group")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "创建群聊")
    @PostMapping("/create")
    public R<Long> createGroup(@RequestBody CreateGroupDTO dto) {
        return R.ok(groupService.createGroup(dto.getName(), dto.getMemberIds()));
    }

    @Operation(summary = "获取群详情")
    @GetMapping("/detail/{groupId}")
    public R<GroupVO> getGroupDetail(@PathVariable Long groupId) {
        return R.ok(groupService.getGroupDetail(groupId));
    }

    @Operation(summary = "获取群成员列表")
    @GetMapping("/members/{groupId}")
    public R<List<GroupMemberVO>> getGroupMembers(@PathVariable Long groupId) {
        return R.ok(groupService.getGroupMembers(groupId));
    }

    @Operation(summary = "更新群资料")
    @PutMapping("/info/{groupId}")
    public R<Void> updateGroupInfo(@PathVariable Long groupId,
                                    @RequestBody UpdateGroupDTO dto) {
        groupService.updateGroupInfo(groupId, dto.getName(), dto.getDescription(), dto.getNotice());
        return R.ok();
    }

    @Operation(summary = "设置/取消管理员")
    @PostMapping("/admin/set")
    public R<Void> setAdmin(@RequestBody SetAdminDTO dto) {
        groupService.setAdmin(dto.getGroupId(), dto.getUserId(), dto.getType());
        return R.ok();
    }

    @Operation(summary = "踢出成员")
    @PostMapping("/member/kick")
    public R<Void> kickMembers(@RequestBody KickMemberDTO dto) {
        groupService.kickMembers(dto.getGroupId(), dto.getMemberIds());
        return R.ok();
    }

    @Operation(summary = "禁言成员")
    @PostMapping("/member/mute")
    public R<Void> muteMember(@RequestBody MuteMemberDTO dto) {
        groupService.muteMember(dto.getGroupId(), dto.getMemberId(), dto.getDuration());
        return R.ok();
    }

    @Operation(summary = "全员禁言")
    @PostMapping("/mute-all/{groupId}")
    public R<Void> muteAll(@PathVariable Long groupId,
                           @RequestParam boolean mute) {
        groupService.muteAll(groupId, mute);
        return R.ok();
    }

    @Operation(summary = "修改我的群名片")
    @PostMapping("/member/nick")
    public R<Void> updateMyGroupNick(@RequestBody UpdateNickDTO dto) {
        groupService.updateMyGroupNick(dto.getGroupId(), dto.getNickname());
        return R.ok();
    }

    @Operation(summary = "转让群主")
    @PostMapping("/transfer")
    public R<Void> transferOwner(@RequestBody TransferOwnerDTO dto) {
        groupService.transferOwner(dto.getGroupId(), dto.getNewOwnerId());
        return R.ok();
    }

    @Operation(summary = "退出群聊")
    @PostMapping("/quit/{groupId}")
    public R<Void> quitGroup(@PathVariable Long groupId) {
        groupService.quitGroup(groupId);
        return R.ok();
    }

    @Operation(summary = "解散群聊")
    @PostMapping("/dissolve/{groupId}")
    public R<Void> dissolveGroup(@PathVariable Long groupId) {
        groupService.dissolveGroup(groupId);
        return R.ok();
    }

    @Operation(summary = "邀请好友入群")
    @PostMapping("/invite")
    public R<Void> inviteMembers(@RequestBody InviteMemberDTO dto) {
        groupService.inviteMembers(dto.getGroupId(), dto.getUserIds());
        return R.ok();
    }

    // ========== DTO 内部类 ==========

    @Data
    public static class CreateGroupDTO {
        private String name;
        private List<Long> memberIds;
    }

    @Data
    public static class UpdateGroupDTO {
        private String name;
        private String description;
        private String notice;
    }

    @Data
    public static class SetAdminDTO {
        private Long groupId;
        private Long userId;
        private Integer type; // 1-设置, 0-取消
    }

    @Data
    public static class KickMemberDTO {
        private Long groupId;
        private List<Long> memberIds;
    }

    @Data
    public static class MuteMemberDTO {
        private Long groupId;
        private Long memberId;
        private Integer duration; // 分钟
    }

    @Data
    public static class UpdateNickDTO {
        private Long groupId;
        private String nickname;
    }

    @Data
    public static class TransferOwnerDTO {
        private Long groupId;
        private Long newOwnerId;
    }

    @Data
    public static class InviteMemberDTO {
        private Long groupId;
        private List<Long> userIds;
    }
}
