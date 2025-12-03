package com.jelly.cinema.im.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.dto.FriendRemarkDTO;
import com.jelly.cinema.im.domain.vo.FriendVO;
import com.jelly.cinema.im.service.FriendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友管理控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "好友管理")
@RestController
@RequestMapping("/im/friend")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @Operation(summary = "获取好友列表")
    @GetMapping("/list")
    public R<List<FriendVO>> getFriendList() {
        Long userId = LoginHelper.getUserId();
        return R.ok(friendService.getFriendList(userId));
    }

    @Operation(summary = "删除好友")
    @PostMapping("/delete/{friendId}")
    public R<Void> deleteFriend(@PathVariable Long friendId,
                                @RequestParam(defaultValue = "false") Boolean keepMessages) {
        Long userId = LoginHelper.getUserId();
        friendService.deleteFriend(userId, friendId, keepMessages);
        return R.ok();
    }

    @Operation(summary = "设置好友备注")
    @PostMapping("/remark")
    public R<Void> setRemark(@Validated @RequestBody FriendRemarkDTO dto) {
        Long userId = LoginHelper.getUserId();
        friendService.setRemark(userId, dto.getFriendId(), dto.getRemark());
        return R.ok();
    }

    @Operation(summary = "拉黑好友")
    @PostMapping("/block/{friendId}")
    public R<Void> blockFriend(@PathVariable Long friendId) {
        Long userId = LoginHelper.getUserId();
        friendService.blockFriend(userId, friendId);
        return R.ok();
    }

    @Operation(summary = "解除拉黑")
    @PostMapping("/unblock/{friendId}")
    public R<Void> unblockFriend(@PathVariable Long friendId) {
        Long userId = LoginHelper.getUserId();
        friendService.unblockFriend(userId, friendId);
        return R.ok();
    }

    @Operation(summary = "获取黑名单列表")
    @GetMapping("/blacklist")
    public R<List<FriendVO>> getBlacklist() {
        Long userId = LoginHelper.getUserId();
        return R.ok(friendService.getBlacklist(userId));
    }
}
