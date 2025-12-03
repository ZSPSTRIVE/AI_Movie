package com.jelly.cinema.im.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.dto.ApplyHandleDTO;
import com.jelly.cinema.im.domain.dto.FriendApplyDTO;
import com.jelly.cinema.im.domain.dto.GroupApplyDTO;
import com.jelly.cinema.im.domain.entity.ApplyRecord;
import com.jelly.cinema.im.domain.entity.Friend;
import com.jelly.cinema.im.domain.entity.Group;
import com.jelly.cinema.im.domain.entity.GroupMember;
import com.jelly.cinema.im.domain.vo.ApplyRecordVO;
import com.jelly.cinema.im.mapper.ApplyRecordMapper;
import com.jelly.cinema.im.mapper.FriendMapper;
import com.jelly.cinema.im.mapper.GroupMapper;
import com.jelly.cinema.im.mapper.GroupMemberMapper;
import com.jelly.cinema.im.service.ApplyService;
import com.jelly.cinema.im.websocket.ChatWebSocketHandler;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 申请服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyServiceImpl implements ApplyService {

    private final ApplyRecordMapper applyRecordMapper;
    private final FriendMapper friendMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final RemoteUserService remoteUserService;
    private final ChatWebSocketHandler webSocketHandler;

    @Override
    public void applyFriend(FriendApplyDTO dto) {
        Long currentUserId = LoginHelper.getUserId();
        log.info("发起好友申请: currentUserId={}, targetId={}", currentUserId, dto.getTargetId());
        
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        if (currentUserId.equals(dto.getTargetId())) {
            throw new ServiceException("不能添加自己为好友");
        }

        // 检查是否已经是好友（检查双向关系）
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.and(w -> w
                .and(w1 -> w1.eq(Friend::getUserId, currentUserId).eq(Friend::getFriendId, dto.getTargetId()))
                .or(w2 -> w2.eq(Friend::getUserId, dto.getTargetId()).eq(Friend::getFriendId, currentUserId))
        );
        if (friendMapper.selectCount(friendWrapper) > 0) {
            log.info("已是好友: currentUserId={}, targetId={}", currentUserId, dto.getTargetId());
            throw new ServiceException("对方已经是您的好友");
        }

        // 检查是否有待处理的申请（双向检查）
        LambdaQueryWrapper<ApplyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplyRecord::getType, ApplyRecord.TYPE_FRIEND)
                .eq(ApplyRecord::getStatus, ApplyRecord.STATUS_PENDING)
                .and(w -> w
                        .and(w1 -> w1.eq(ApplyRecord::getFromId, currentUserId).eq(ApplyRecord::getTargetId, dto.getTargetId()))
                        .or(w2 -> w2.eq(ApplyRecord::getFromId, dto.getTargetId()).eq(ApplyRecord::getTargetId, currentUserId))
                );
        if (applyRecordMapper.selectCount(wrapper) > 0) {
            log.info("已有待处理的申请: currentUserId={}, targetId={}", currentUserId, dto.getTargetId());
            throw new ServiceException("已发送过申请或对方已向您发送申请，请查看申请列表");
        }

        // 创建申请记录
        ApplyRecord record = new ApplyRecord();
        record.setType(ApplyRecord.TYPE_FRIEND);
        record.setFromId(currentUserId);
        record.setTargetId(dto.getTargetId());
        record.setReason(dto.getReason());
        record.setRemark(dto.getRemark());
        record.setStatus(ApplyRecord.STATUS_PENDING);
        applyRecordMapper.insert(record);

        log.info("好友申请已发送: from={}, to={}, recordId={}", currentUserId, dto.getTargetId(), record.getId());
        
        // 通过 WebSocket 通知目标用户
        notifyUser(dto.getTargetId(), "friend_apply", "您有一条新的好友申请", record.getId());
    }

    @Override
    public void applyGroup(GroupApplyDTO dto) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        // 检查群是否存在
        Group group = groupMapper.selectById(dto.getGroupId());
        if (group == null || group.getStatus() != 0) {
            throw new ServiceException("群聊不存在或已解散");
        }

        // 检查是否已经是群成员
        LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
        memberWrapper.eq(GroupMember::getGroupId, dto.getGroupId())
                .eq(GroupMember::getUserId, currentUserId);
        if (groupMemberMapper.selectCount(memberWrapper) > 0) {
            throw new ServiceException("您已经是该群成员");
        }

        // 检查群是否满员
        if (group.getMemberCount() >= group.getMaxMember()) {
            throw new ServiceException("该群已满员");
        }

        // 检查加群方式
        if (group.getJoinType() == 2) {
            throw new ServiceException("该群禁止加入");
        }

        // 自由加入模式，直接加入
        if (group.getJoinType() == 0) {
            addGroupMember(dto.getGroupId(), currentUserId, GroupMember.ROLE_MEMBER);
            return;
        }

        // 需要验证模式，检查是否有待处理的申请
        LambdaQueryWrapper<ApplyRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApplyRecord::getType, ApplyRecord.TYPE_GROUP)
                .eq(ApplyRecord::getFromId, currentUserId)
                .eq(ApplyRecord::getTargetId, dto.getGroupId())
                .eq(ApplyRecord::getStatus, ApplyRecord.STATUS_PENDING);
        if (applyRecordMapper.selectCount(wrapper) > 0) {
            throw new ServiceException("已发送过申请，请等待审核");
        }

        // 创建申请记录
        ApplyRecord record = new ApplyRecord();
        record.setType(ApplyRecord.TYPE_GROUP);
        record.setFromId(currentUserId);
        record.setTargetId(dto.getGroupId());
        record.setReason(dto.getReason());
        record.setStatus(ApplyRecord.STATUS_PENDING);
        applyRecordMapper.insert(record);

        log.info("入群申请已发送: from={}, groupId={}", currentUserId, dto.getGroupId());
        
        // 通过 WebSocket 通知群管理员
        List<Long> adminIds = getGroupAdminIds(dto.getGroupId());
        for (Long adminId : adminIds) {
            notifyUser(adminId, "group_apply", "您有一条新的入群申请", record.getId());
        }
    }

    @Override
    public List<ApplyRecordVO> getApplyList(Integer type) {
        Long currentUserId = LoginHelper.getUserId();
        log.info("获取申请列表开始: currentUserId={}, type={}", currentUserId, type);
        
        if (currentUserId == null) {
            log.warn("获取申请列表失败: 用户未登录");
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ApplyRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (type != null && type == ApplyRecord.TYPE_FRIEND) {
            // 好友申请：我收到的
            log.info("查询好友申请: targetId={}", currentUserId);
            wrapper.eq(ApplyRecord::getType, ApplyRecord.TYPE_FRIEND)
                    .eq(ApplyRecord::getTargetId, currentUserId);
        } else if (type != null && type == ApplyRecord.TYPE_GROUP) {
            // 入群申请：我管理的群
            List<Long> managedGroupIds = getAdminGroupIds(currentUserId);
            log.info("查询入群申请: 管理的群={}", managedGroupIds);
            if (managedGroupIds.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.eq(ApplyRecord::getType, ApplyRecord.TYPE_GROUP)
                    .in(ApplyRecord::getTargetId, managedGroupIds);
        } else {
            // 全部：好友申请(我收到的) + 入群申请(我管理的群)
            List<Long> managedGroupIds = getAdminGroupIds(currentUserId);
            log.info("查询全部申请: currentUserId={}, 管理的群={}", currentUserId, managedGroupIds);
            wrapper.and(w -> {
                w.and(w1 -> w1.eq(ApplyRecord::getType, ApplyRecord.TYPE_FRIEND)
                        .eq(ApplyRecord::getTargetId, currentUserId));
                if (!managedGroupIds.isEmpty()) {
                    w.or(w2 -> w2.eq(ApplyRecord::getType, ApplyRecord.TYPE_GROUP)
                            .in(ApplyRecord::getTargetId, managedGroupIds));
                }
            });
        }

        wrapper.orderByDesc(ApplyRecord::getCreateTime);
        List<ApplyRecord> records = applyRecordMapper.selectList(wrapper);
        log.info("获取申请列表结束: 记录数={}", records.size());

        return toVOList(records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleApply(ApplyHandleDTO dto) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        ApplyRecord record = applyRecordMapper.selectById(dto.getApplyId());
        if (record == null) {
            throw new ServiceException("申请不存在");
        }
        if (record.getStatus() != ApplyRecord.STATUS_PENDING) {
            throw new ServiceException("该申请已处理");
        }

        // 验证权限
        if (record.getType() == ApplyRecord.TYPE_FRIEND) {
            if (!currentUserId.equals(record.getTargetId())) {
                throw new ServiceException("无权处理该申请");
            }
        } else {
            // 入群申请需要管理员权限
            if (!isGroupAdmin(record.getTargetId(), currentUserId)) {
                throw new ServiceException("无权处理该申请");
            }
        }

        // 更新申请状态
        record.setStatus(dto.getStatus());
        record.setHandlerId(currentUserId);
        record.setHandleTime(LocalDateTime.now());
        applyRecordMapper.updateById(record);

        // 如果同意，执行相应操作
        if (dto.getStatus() == ApplyRecord.STATUS_ACCEPTED) {
            if (record.getType() == ApplyRecord.TYPE_FRIEND) {
                // 添加好友（双向）
                addFriend(record.getFromId(), record.getTargetId(), 
                        record.getRemark(), dto.getGroupName());
                addFriend(record.getTargetId(), record.getFromId(), 
                        null, "我的好友");
                log.info("好友添加成功: {} <-> {}", record.getFromId(), record.getTargetId());
            } else {
                // 加入群
                addGroupMember(record.getTargetId(), record.getFromId(), GroupMember.ROLE_MEMBER);
                log.info("用户 {} 加入群 {}", record.getFromId(), record.getTargetId());
            }
        }

        // 通过 WebSocket 通知申请人结果
        String notifyType = dto.getStatus() == ApplyRecord.STATUS_ACCEPTED ? "apply_accepted" : "apply_rejected";
        String message = dto.getStatus() == ApplyRecord.STATUS_ACCEPTED ? "您的申请已通过" : "您的申请已被拒绝";
        notifyUser(record.getFromId(), notifyType, message, record.getId());
    }

    @Override
    public int getUnreadCount() {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            return 0;
        }

        LambdaQueryWrapper<ApplyRecord> wrapper = new LambdaQueryWrapper<>();
        
        // 好友申请 + 入群申请（我管理的群）
        List<Long> managedGroupIds = getAdminGroupIds(currentUserId);
        wrapper.eq(ApplyRecord::getStatus, ApplyRecord.STATUS_PENDING)
                .and(w -> {
                    w.and(w1 -> w1.eq(ApplyRecord::getType, ApplyRecord.TYPE_FRIEND)
                            .eq(ApplyRecord::getTargetId, currentUserId));
                    if (!managedGroupIds.isEmpty()) {
                        w.or(w2 -> w2.eq(ApplyRecord::getType, ApplyRecord.TYPE_GROUP)
                                .in(ApplyRecord::getTargetId, managedGroupIds));
                    }
                });

        return Math.toIntExact(applyRecordMapper.selectCount(wrapper));
    }

    // ========== 私有方法 ==========

    private void addFriend(Long userId, Long friendId, String remark, String groupName) {
        // 先检查是否存在（包括已删除的记录），避免唯一约束冲突
        Friend existing = friendMapper.selectIncludeDeleted(userId, friendId);
        
        if (existing != null) {
            // 如果记录存在（可能是之前删除的），恢复并更新
            friendMapper.restoreFriend(userId, friendId, remark);
            log.info("恢复好友关系: userId={}, friendId={}", userId, friendId);
        } else {
            // 不存在则新建
            Friend friend = new Friend();
            friend.setUserId(userId);
            friend.setFriendId(friendId);
            friend.setRemark(remark);
            friend.setStatus(0);
            friendMapper.insert(friend);
            log.info("创建好友关系: userId={}, friendId={}", userId, friendId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void addGroupMember(Long groupId, Long userId, int role) {
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinTime(LocalDateTime.now());
        groupMemberMapper.insert(member);

        // 更新群成员数
        Group group = groupMapper.selectById(groupId);
        if (group != null) {
            group.setMemberCount(group.getMemberCount() + 1);
            groupMapper.updateById(group);
        }
    }

    private List<Long> getAdminGroupIds(Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getUserId, userId)
                .ge(GroupMember::getRole, GroupMember.ROLE_ADMIN);
        List<GroupMember> members = groupMemberMapper.selectList(wrapper);
        return members.stream().map(GroupMember::getGroupId).collect(Collectors.toList());
    }

    private boolean isGroupAdmin(Long groupId, Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId)
                .ge(GroupMember::getRole, GroupMember.ROLE_ADMIN);
        return groupMemberMapper.selectCount(wrapper) > 0;
    }
    
    private List<Long> getGroupAdminIds(Long groupId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .ge(GroupMember::getRole, GroupMember.ROLE_ADMIN);
        List<GroupMember> members = groupMemberMapper.selectList(wrapper);
        return members.stream().map(GroupMember::getUserId).collect(Collectors.toList());
    }
    
    private void notifyUser(Long userId, String type, String message, Long applyId) {
        if (webSocketHandler.isOnline(userId)) {
            String json = JSONUtil.toJsonStr(Map.of(
                    "type", type,
                    "message", message,
                    "applyId", applyId
            ));
            webSocketHandler.sendToUser(userId, json);
        }
    }

    private List<ApplyRecordVO> toVOList(List<ApplyRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取所有相关用户ID
        Set<Long> userIds = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        for (ApplyRecord r : records) {
            userIds.add(r.getFromId());
            if (r.getType() == ApplyRecord.TYPE_GROUP) {
                groupIds.add(r.getTargetId());
            }
        }

        // 批量查询用户信息
        Map<Long, RemoteUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            R<List<RemoteUser>> result = remoteUserService.getUsersByIds(new ArrayList<>(userIds));
            if (result.isSuccess() && result.getData() != null) {
                userMap = result.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u));
            }
        }

        // 批量查询群信息
        Map<Long, Group> groupMap = new HashMap<>();
        if (!groupIds.isEmpty()) {
            List<Group> groups = groupMapper.selectBatchIds(groupIds);
            groupMap = groups.stream().collect(Collectors.toMap(Group::getId, g -> g));
        }

        // 转换为 VO
        Map<Long, RemoteUser> finalUserMap = userMap;
        Map<Long, Group> finalGroupMap = groupMap;
        return records.stream().map(r -> {
            ApplyRecordVO vo = BeanUtil.copyProperties(r, ApplyRecordVO.class);
            
            RemoteUser fromUser = finalUserMap.get(r.getFromId());
            if (fromUser != null) {
                vo.setFromNickname(fromUser.getNickname());
                vo.setFromAvatar(fromUser.getAvatar());
            }

            if (r.getType() == ApplyRecord.TYPE_GROUP) {
                Group group = finalGroupMap.get(r.getTargetId());
                if (group != null) {
                    vo.setTargetName(group.getName());
                }
            }

            return vo;
        }).collect(Collectors.toList());
    }
}
