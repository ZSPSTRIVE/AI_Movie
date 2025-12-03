package com.jelly.cinema.im.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.api.domain.*;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.domain.entity.Friend;
import com.jelly.cinema.im.domain.entity.Group;
import com.jelly.cinema.im.domain.entity.GroupMember;
import com.jelly.cinema.im.mapper.ChatMessageMapper;
import com.jelly.cinema.im.mapper.FriendMapper;
import com.jelly.cinema.im.mapper.GroupMapper;
import com.jelly.cinema.im.mapper.GroupMemberMapper;
import com.jelly.cinema.im.service.ImAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IM 管理端服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImAdminServiceImpl implements ImAdminService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final FriendMapper friendMapper;
    private final RemoteUserService remoteUserService;

    @Override
    public List<RemoteGroup> getGroups(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Group::getDeleted, 0);
        
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w
                    .like(Group::getName, keyword)
                    .or()
                    .like(Group::getGroupNo, keyword)
            );
        }
        
        wrapper.orderByDesc(Group::getCreateTime);
        
        Page<Group> page = groupMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        
        // 收集群主ID
        Set<Long> ownerIds = page.getRecords().stream()
                .map(Group::getOwnerId)
                .collect(Collectors.toSet());
        
        // 批量查询群主信息
        Map<Long, RemoteUser> userMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            R<List<RemoteUser>> userResult = remoteUserService.getUsersByIds(new ArrayList<>(ownerIds));
            if (userResult.isSuccess() && userResult.getData() != null) {
                userMap = userResult.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
            }
        }
        
        // 转换为 RemoteGroup
        List<RemoteGroup> result = new ArrayList<>();
        for (Group group : page.getRecords()) {
            RemoteGroup rg = new RemoteGroup();
            rg.setId(group.getId());
            rg.setGroupNo(group.getGroupNo());
            rg.setName(group.getName());
            rg.setAvatar(group.getAvatar());
            rg.setDescription(group.getDescription());
            rg.setOwnerId(group.getOwnerId());
            rg.setMemberCount(group.getMemberCount());
            rg.setMaxMember(group.getMaxMember());
            rg.setStatus(group.getStatus());
            rg.setCreateTime(group.getCreateTime());
            
            RemoteUser owner = userMap.get(group.getOwnerId());
            rg.setOwnerNickname(owner != null ? owner.getNickname() : "未知用户");
            
            result.add(rg);
        }
        
        return result;
    }

    @Override
    public Long getGroupCount() {
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Group::getDeleted, 0);
        return groupMapper.selectCount(wrapper);
    }

    @Override
    public Integer getActiveGroupCount() {
        // 7天内有消息的群组数
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getCmdType, 2) // 群聊消息
               .ge(ChatMessage::getCreateTime, sevenDaysAgo)
               .select(ChatMessage::getToId);
        
        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        Set<Long> activeGroupIds = messages.stream()
                .map(ChatMessage::getToId)
                .collect(Collectors.toSet());
        
        return activeGroupIds.size();
    }

    @Override
    public void dismissGroup(Long groupId, String reason) {
        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            return;
        }
        
        // 更新群状态为已解散
        group.setStatus(1);
        groupMapper.updateById(group);
        
        // 删除所有群成员
        LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMember::getGroupId, groupId);
        groupMemberMapper.delete(memberWrapper);
        
        log.info("群组 {} 已被管理员解散，原因: {}", groupId, reason);
    }

    @Override
    public List<RemoteMessage> getGroupMessages(Long groupId, int pageNum, int pageSize) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getCmdType, 2) // 群聊消息
               .eq(ChatMessage::getToId, groupId)
               .eq(ChatMessage::getStatus, 0) // 未撤回
               .orderByDesc(ChatMessage::getCreateTime);
        
        Page<ChatMessage> page = chatMessageMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        
        // 收集发送者ID
        Set<Long> senderIds = page.getRecords().stream()
                .map(ChatMessage::getFromId)
                .collect(Collectors.toSet());
        
        // 批量查询发送者信息
        Map<Long, RemoteUser> userMap = new HashMap<>();
        if (!senderIds.isEmpty()) {
            R<List<RemoteUser>> userResult = remoteUserService.getUsersByIds(new ArrayList<>(senderIds));
            if (userResult.isSuccess() && userResult.getData() != null) {
                userMap = userResult.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
            }
        }
        
        // 转换为 RemoteMessage
        List<RemoteMessage> result = new ArrayList<>();
        for (ChatMessage msg : page.getRecords()) {
            RemoteMessage rm = new RemoteMessage();
            rm.setId(msg.getId());
            rm.setSessionId(msg.getSessionId());
            rm.setSenderId(msg.getFromId());
            rm.setMsgType(msg.getMsgType());
            rm.setContent(msg.getContent());
            rm.setCreateTime(msg.getCreateTime());
            
            RemoteUser sender = userMap.get(msg.getFromId());
            if (sender != null) {
                rm.setSenderName(sender.getNickname());
                rm.setSenderAvatar(sender.getAvatar());
            } else {
                rm.setSenderName("未知用户");
            }
            
            result.add(rm);
        }
        
        return result;
    }

    @Override
    public Long getMessageCount(Long groupId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        if (groupId != null) {
            wrapper.eq(ChatMessage::getCmdType, 2)
                   .eq(ChatMessage::getToId, groupId);
        }
        return chatMessageMapper.selectCount(wrapper);
    }

    @Override
    public List<RemoteFriend> getUserFriends(Long userId) {
        log.info("获取用户好友列表开始: userId={}", userId);
        
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
               .eq(Friend::getStatus, 0)
               .eq(Friend::getDeleted, 0);
        
        List<Friend> friends = friendMapper.selectList(wrapper);
        log.info("查询到好友记录数: {}", friends.size());
        
        if (friends.isEmpty()) {
            log.info("获取用户好友列表结束: userId={}, 好友数=0", userId);
            return Collections.emptyList();
        }
        
        // 批量查询好友信息
        List<Long> friendIds = friends.stream()
                .map(Friend::getFriendId)
                .collect(Collectors.toList());
        log.info("好友ID列表: {}", friendIds);
        
        Map<Long, RemoteUser> userMap = new HashMap<>();
        try {
            R<List<RemoteUser>> userResult = remoteUserService.getUsersByIds(friendIds);
            if (userResult.isSuccess() && userResult.getData() != null) {
                userMap = userResult.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
                log.info("查询到好友用户信息数: {}", userMap.size());
            } else {
                log.warn("查询好友用户信息失败: code={}, msg={}", 
                        userResult != null ? userResult.getCode() : "null",
                        userResult != null ? userResult.getMsg() : "null");
            }
        } catch (Exception e) {
            log.error("调用远程用户服务失败", e);
        }
        
        // 转换为 RemoteFriend
        List<RemoteFriend> result = new ArrayList<>();
        for (Friend friend : friends) {
            RemoteFriend rf = new RemoteFriend();
            rf.setId(friend.getFriendId());
            rf.setRemark(friend.getRemark());
            
            RemoteUser user = userMap.get(friend.getFriendId());
            if (user != null) {
                rf.setNickname(user.getNickname());
                rf.setAvatar(user.getAvatar());
            } else {
                rf.setNickname("未知用户");
            }
            
            result.add(rf);
        }
        
        log.info("获取用户好友列表结束: userId={}, 好友数={}", userId, result.size());
        return result;
    }

    @Override
    public List<RemoteGroupSimple> getUserGroups(Long userId) {
        // 查询用户加入的群ID
        List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
        
        if (groupIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 查询群组信息
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Group::getId, groupIds)
               .eq(Group::getStatus, 0)
               .eq(Group::getDeleted, 0);
        
        List<Group> groups = groupMapper.selectList(wrapper);
        
        // 转换为 RemoteGroupSimple
        return groups.stream().map(g -> {
            RemoteGroupSimple rgs = new RemoteGroupSimple();
            rgs.setId(g.getId());
            rgs.setName(g.getName());
            rgs.setAvatar(g.getAvatar());
            rgs.setMemberCount(g.getMemberCount());
            return rgs;
        }).collect(Collectors.toList());
    }
}
