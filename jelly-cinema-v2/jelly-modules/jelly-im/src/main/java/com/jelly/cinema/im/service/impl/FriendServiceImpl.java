package com.jelly.cinema.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.domain.entity.Friend;
import com.jelly.cinema.im.domain.vo.FriendVO;
import com.jelly.cinema.im.mapper.ChatMessageMapper;
import com.jelly.cinema.im.mapper.FriendMapper;
import com.jelly.cinema.im.service.FriendService;
import com.jelly.cinema.im.websocket.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 好友服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final FriendMapper friendMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RemoteUserService remoteUserService;
    private final ChatWebSocketHandler webSocketHandler;

    @Override
    public List<FriendVO> getFriendList(Long userId) {
        log.info("获取好友列表: userId={}", userId);

        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getStatus, 0) // 只查正常好友，不包括拉黑的
                .eq(Friend::getDeleted, 0);

        List<Friend> friends = friendMapper.selectList(wrapper);
        if (friends.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询好友用户信息
        List<Long> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toList());

        Map<Long, RemoteUser> userMap = getUserMap(friendIds);

        // 转换为 VO
        List<FriendVO> result = new ArrayList<>();
        for (Friend friend : friends) {
            FriendVO vo = new FriendVO();
            vo.setId(friend.getFriendId());
            vo.setRemark(friend.getRemark());
            vo.setStatus(friend.getStatus());
            vo.setCreateTime(friend.getCreateTime());

            RemoteUser user = userMap.get(friend.getFriendId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
                vo.setSignature(user.getSignature());
            } else {
                vo.setNickname("未知用户");
            }

            // 检查是否在线
            vo.setOnline(webSocketHandler.isOnline(friend.getFriendId()));

            result.add(vo);
        }

        log.info("获取好友列表结束: userId={}, count={}", userId, result.size());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFriend(Long userId, Long friendId, Boolean keepMessages) {
        log.info("删除好友: userId={}, friendId={}, keepMessages={}", userId, friendId, keepMessages);

        // 删除双向好友关系
        LambdaUpdateWrapper<Friend> wrapper1 = new LambdaUpdateWrapper<>();
        wrapper1.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        friendMapper.delete(wrapper1);

        LambdaUpdateWrapper<Friend> wrapper2 = new LambdaUpdateWrapper<>();
        wrapper2.eq(Friend::getUserId, friendId)
                .eq(Friend::getFriendId, userId);
        friendMapper.delete(wrapper2);

        // 如果不保留消息，删除聊天记录
        if (!Boolean.TRUE.equals(keepMessages)) {
            String sessionId = generateSessionId(userId, friendId);
            LambdaUpdateWrapper<ChatMessage> msgWrapper = new LambdaUpdateWrapper<>();
            msgWrapper.eq(ChatMessage::getSessionId, sessionId);
            chatMessageMapper.delete(msgWrapper);
            log.info("删除聊天记录: sessionId={}", sessionId);
        }

        log.info("删除好友成功: userId={}, friendId={}", userId, friendId);
    }

    @Override
    public void setRemark(Long userId, Long friendId, String remark) {
        log.info("设置好友备注: userId={}, friendId={}, remark={}", userId, friendId, remark);

        LambdaUpdateWrapper<Friend> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .set(Friend::getRemark, remark);

        int rows = friendMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("好友关系不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blockFriend(Long userId, Long friendId) {
        log.info("拉黑好友: userId={}, friendId={}", userId, friendId);

        LambdaUpdateWrapper<Friend> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .set(Friend::getStatus, 1); // 1-拉黑

        int rows = friendMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("好友关系不存在");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unblockFriend(Long userId, Long friendId) {
        log.info("解除拉黑: userId={}, friendId={}", userId, friendId);

        LambdaUpdateWrapper<Friend> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId)
                .set(Friend::getStatus, 0); // 0-正常

        int rows = friendMapper.update(null, wrapper);
        if (rows == 0) {
            throw new ServiceException("好友关系不存在");
        }
    }

    @Override
    public List<FriendVO> getBlacklist(Long userId) {
        log.info("获取黑名单: userId={}", userId);

        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getStatus, 1) // 只查拉黑的
                .eq(Friend::getDeleted, 0);

        List<Friend> friends = friendMapper.selectList(wrapper);
        if (friends.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toList());

        Map<Long, RemoteUser> userMap = getUserMap(friendIds);

        List<FriendVO> result = new ArrayList<>();
        for (Friend friend : friends) {
            FriendVO vo = new FriendVO();
            vo.setId(friend.getFriendId());
            vo.setRemark(friend.getRemark());
            vo.setStatus(friend.getStatus());
            vo.setCreateTime(friend.getCreateTime());

            RemoteUser user = userMap.get(friend.getFriendId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            } else {
                vo.setNickname("未知用户");
            }

            result.add(vo);
        }

        return result;
    }

    /**
     * 批量获取用户信息
     */
    private Map<Long, RemoteUser> getUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<RemoteUser>> result = remoteUserService.getUsersByIds(userIds);
            if (result.isSuccess() && result.getData() != null) {
                return result.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.error("查询用户信息失败", e);
        }
        return Collections.emptyMap();
    }

    /**
     * 生成私聊会话ID
     */
    private String generateSessionId(Long userId1, Long userId2) {
        long min = Math.min(userId1, userId2);
        long max = Math.max(userId1, userId2);
        return "private_" + min + "_" + max;
    }
}
