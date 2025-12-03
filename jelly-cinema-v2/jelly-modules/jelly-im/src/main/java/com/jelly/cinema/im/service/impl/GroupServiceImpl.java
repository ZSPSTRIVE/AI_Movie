package com.jelly.cinema.im.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.entity.Group;
import com.jelly.cinema.im.domain.entity.GroupMember;
import com.jelly.cinema.im.domain.vo.GroupMemberVO;
import com.jelly.cinema.im.domain.vo.GroupVO;
import com.jelly.cinema.im.mapper.GroupMapper;
import com.jelly.cinema.im.mapper.GroupMemberMapper;
import com.jelly.cinema.im.service.GroupService;
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
 * 群组服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final RemoteUserService remoteUserService;
    private final ChatWebSocketHandler webSocketHandler;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(String name, List<Long> memberIds) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        // 创建群
        Group group = new Group();
        group.setGroupNo(generateGroupNo());
        group.setName(name);
        group.setOwnerId(currentUserId);
        group.setMaxMember(200);
        group.setMemberCount(1);
        group.setJoinType(1); // 默认需要验证
        group.setIsMuteAll(0);
        group.setStatus(0);
        groupMapper.insert(group);

        // 添加群主
        addMember(group.getId(), currentUserId, GroupMember.ROLE_OWNER);

        // 添加初始成员
        if (memberIds != null && !memberIds.isEmpty()) {
            for (Long memberId : memberIds) {
                if (!memberId.equals(currentUserId)) {
                    addMember(group.getId(), memberId, GroupMember.ROLE_MEMBER);
                    group.setMemberCount(group.getMemberCount() + 1);
                }
            }
            groupMapper.updateById(group);
        }

        log.info("群聊创建成功: groupId={}, name={}, owner={}", group.getId(), name, currentUserId);
        return group.getId();
    }

    @Override
    public GroupVO getGroupDetail(Long groupId) {
        Long currentUserId = LoginHelper.getUserId();
        log.info("获取群详情开始: groupId={}, currentUserId={}", groupId, currentUserId);

        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            log.warn("获取群详情失败: groupId={} 对应群不存在", groupId);
            throw new ServiceException("群聊不存在或已解散");
        }
        if (group.getStatus() != 0) {
            log.warn("获取群详情失败: groupId={} 群状态异常 status={}", groupId, group.getStatus());
            throw new ServiceException("群聊不存在或已解散");
        }

        GroupVO vo = BeanUtil.copyProperties(group, GroupVO.class);

        // 获取群主信息
        R<RemoteUser> ownerResult = remoteUserService.getUserById(group.getOwnerId());
        if (ownerResult.isSuccess() && ownerResult.getData() != null) {
            vo.setOwnerNickname(ownerResult.getData().getNickname());
        }

        // 获取当前用户在群中的角色
        if (currentUserId != null) {
            GroupMember myMember = getMember(groupId, currentUserId);
            if (myMember != null) {
                vo.setMyRole(myMember.getRole());
                vo.setMyGroupNick(myMember.getGroupNick());
                log.info("获取群详情: 当前用户在群中的角色 role={}, groupNick={}", myMember.getRole(), myMember.getGroupNick());
            } else {
                log.warn("获取群详情: 当前用户不在群成员列表中, groupId={}, userId={}", groupId, currentUserId);
            }
        } else {
            log.warn("获取群详情: 当前未登录用户访问, groupId={}", groupId);
        }

        // 获取前15个成员
        vo.setMembers(getGroupMembers(groupId, 15));
        log.info("获取群详情结束: groupId={}, memberCount={}", groupId,
                (vo.getMembers() != null ? vo.getMembers().size() : 0));

        return vo;
    }

    @Override
    public List<GroupMemberVO> getGroupMembers(Long groupId) {
        return getGroupMembers(groupId, null);
    }

    private List<GroupMemberVO> getGroupMembers(Long groupId, Integer limit) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .orderByDesc(GroupMember::getRole)
                .orderByAsc(GroupMember::getJoinTime);
        if (limit != null) {
            wrapper.last("LIMIT " + limit);
        }

        List<GroupMember> members = groupMemberMapper.selectList(wrapper);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }

        // 获取用户信息
        List<Long> userIds = members.stream().map(GroupMember::getUserId).collect(Collectors.toList());
        Map<Long, RemoteUser> userMap = new HashMap<>();
        R<List<RemoteUser>> result = remoteUserService.getUsersByIds(userIds);
        if (result.isSuccess() && result.getData() != null) {
            userMap = result.getData().stream().collect(Collectors.toMap(RemoteUser::getId, u -> u));
        }

        Map<Long, RemoteUser> finalUserMap = userMap;
        return members.stream().map(m -> {
            GroupMemberVO vo = new GroupMemberVO();
            vo.setUserId(m.getUserId());
            vo.setGroupNick(m.getGroupNick());
            vo.setRole(m.getRole());
            vo.setMuteEndTime(m.getMuteEndTime());
            vo.setJoinTime(m.getJoinTime());
            vo.setIsMuted(m.isMuted());

            RemoteUser user = finalUserMap.get(m.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }

            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void updateGroupInfo(Long groupId, String name, String description, String notice) {
        checkAdminPermission(groupId);

        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException("群聊不存在");
        }

        if (name != null) group.setName(name);
        if (description != null) group.setDescription(description);
        if (notice != null) group.setNotice(notice);
        groupMapper.updateById(group);

        log.info("群资料更新: groupId={}", groupId);
    }

    @Override
    public void setAdmin(Long groupId, Long userId, int type) {
        checkOwnerPermission(groupId);

        GroupMember member = getMember(groupId, userId);
        if (member == null) {
            throw new ServiceException("该用户不是群成员");
        }
        if (member.isOwner()) {
            throw new ServiceException("不能操作群主");
        }

        member.setRole(type == 1 ? GroupMember.ROLE_ADMIN : GroupMember.ROLE_MEMBER);
        groupMemberMapper.updateById(member);

        log.info("群管理员设置: groupId={}, userId={}, type={}", groupId, userId, type);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void kickMembers(Long groupId, List<Long> memberIds) {
        checkAdminPermission(groupId);

        Long currentUserId = LoginHelper.getUserId();
        GroupMember currentMember = getMember(groupId, currentUserId);

        for (Long memberId : memberIds) {
            GroupMember member = getMember(groupId, memberId);
            if (member == null) continue;
            
            // 不能踢自己
            if (memberId.equals(currentUserId)) continue;
            
            // 管理员不能踢群主和其他管理员
            if (!currentMember.isOwner() && member.isAdmin()) {
                continue;
            }

            groupMemberMapper.deleteById(member.getId());
        }

        // 更新群成员数
        updateMemberCount(groupId);

        log.info("踢出群成员: groupId={}, memberIds={}", groupId, memberIds);
        
        // 通过 WebSocket 通知被踢用户
        Group group = groupMapper.selectById(groupId);
        String groupName = group != null ? group.getName() : "群聊";
        for (Long memberId : memberIds) {
            notifyUser(memberId, "kicked_from_group", "您已被移出群聊「" + groupName + "」", groupId);
        }
    }

    @Override
    public void muteMember(Long groupId, Long memberId, int duration) {
        checkAdminPermission(groupId);

        GroupMember member = getMember(groupId, memberId);
        if (member == null) {
            throw new ServiceException("该用户不是群成员");
        }
        if (member.isAdmin()) {
            throw new ServiceException("不能禁言管理员");
        }

        if (duration <= 0) {
            member.setMuteEndTime(null);
        } else {
            member.setMuteEndTime(LocalDateTime.now().plusMinutes(duration));
        }
        groupMemberMapper.updateById(member);

        log.info("群成员禁言: groupId={}, memberId={}, duration={}min", groupId, memberId, duration);
    }

    @Override
    public void muteAll(Long groupId, boolean mute) {
        checkAdminPermission(groupId);

        Group group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new ServiceException("群聊不存在");
        }

        group.setIsMuteAll(mute ? 1 : 0);
        groupMapper.updateById(group);

        log.info("全员禁言设置: groupId={}, mute={}", groupId, mute);
    }

    @Override
    public void updateMyGroupNick(Long groupId, String nickname) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        GroupMember member = getMember(groupId, currentUserId);
        if (member == null) {
            throw new ServiceException("您不是该群成员");
        }

        member.setGroupNick(nickname);
        groupMemberMapper.updateById(member);

        log.info("群名片修改: groupId={}, userId={}, nickname={}", groupId, currentUserId, nickname);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Long groupId, Long newOwnerId) {
        checkOwnerPermission(groupId);

        Long currentUserId = LoginHelper.getUserId();
        
        GroupMember newOwner = getMember(groupId, newOwnerId);
        if (newOwner == null) {
            throw new ServiceException("该用户不是群成员");
        }

        // 当前群主降为普通成员
        GroupMember currentOwner = getMember(groupId, currentUserId);
        currentOwner.setRole(GroupMember.ROLE_MEMBER);
        groupMemberMapper.updateById(currentOwner);

        // 新群主
        newOwner.setRole(GroupMember.ROLE_OWNER);
        groupMemberMapper.updateById(newOwner);

        // 更新群主ID
        Group group = groupMapper.selectById(groupId);
        group.setOwnerId(newOwnerId);
        groupMapper.updateById(group);

        log.info("群主转让: groupId={}, from={}, to={}", groupId, currentUserId, newOwnerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void quitGroup(Long groupId) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        GroupMember member = getMember(groupId, currentUserId);
        if (member == null) {
            throw new ServiceException("您不是该群成员");
        }

        if (member.isOwner()) {
            throw new ServiceException("群主不能退出，请先转让群主或解散群聊");
        }

        groupMemberMapper.deleteById(member.getId());
        updateMemberCount(groupId);

        log.info("退出群聊: groupId={}, userId={}", groupId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolveGroup(Long groupId) {
        checkOwnerPermission(groupId);

        // 删除所有成员
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId);
        groupMemberMapper.delete(wrapper);

        // 标记群为已解散
        Group group = groupMapper.selectById(groupId);
        group.setStatus(1);
        groupMapper.updateById(group);

        log.info("群聊解散: groupId={}", groupId);
        
        // 通过 WebSocket 通知所有成员
        List<Long> memberIds = groupMemberMapper.selectUserIdsByGroupId(groupId);
        String groupName = group.getName();
        for (Long memberId : memberIds) {
            notifyUser(memberId, "group_dissolved", "群聊「" + groupName + "」已被解散", groupId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inviteMembers(Long groupId, List<Long> userIds) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        GroupMember currentMember = getMember(groupId, currentUserId);
        if (currentMember == null) {
            throw new ServiceException("您不是该群成员");
        }

        Group group = groupMapper.selectById(groupId);
        if (group == null || group.getStatus() != 0) {
            throw new ServiceException("群聊不存在或已解散");
        }

        for (Long userId : userIds) {
            if (getMember(groupId, userId) != null) {
                continue; // 已是群成员
            }
            if (group.getMemberCount() >= group.getMaxMember()) {
                throw new ServiceException("群成员已满");
            }
            addMember(groupId, userId, GroupMember.ROLE_MEMBER);
            group.setMemberCount(group.getMemberCount() + 1);
        }

        groupMapper.updateById(group);
        log.info("邀请入群: groupId={}, userIds={}", groupId, userIds);
    }

    // ========== 私有方法 ==========

    private String generateGroupNo() {
        // 生成 6-8 位数字群号
        String groupNo;
        int retries = 0;
        do {
            int length = RandomUtil.randomInt(6, 9);
            groupNo = RandomUtil.randomNumbers(length);
            // 确保不以 0 开头
            if (groupNo.startsWith("0")) {
                groupNo = RandomUtil.randomInt(1, 10) + groupNo.substring(1);
            }
            retries++;
        } while (isGroupNoExists(groupNo) && retries < 10);

        return groupNo;
    }

    private boolean isGroupNoExists(String groupNo) {
        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Group::getGroupNo, groupNo);
        return groupMapper.selectCount(wrapper) > 0;
    }

    private void addMember(Long groupId, Long userId, int role) {
        GroupMember member = new GroupMember();
        member.setGroupId(groupId);
        member.setUserId(userId);
        member.setRole(role);
        member.setJoinTime(LocalDateTime.now());
        groupMemberMapper.insert(member);
    }

    private GroupMember getMember(Long groupId, Long userId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId)
                .eq(GroupMember::getUserId, userId);
        return groupMemberMapper.selectOne(wrapper);
    }

    private void updateMemberCount(Long groupId) {
        LambdaQueryWrapper<GroupMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GroupMember::getGroupId, groupId);
        long count = groupMemberMapper.selectCount(wrapper);

        Group group = groupMapper.selectById(groupId);
        if (group != null) {
            group.setMemberCount((int) count);
            groupMapper.updateById(group);
        }
    }

    private void checkAdminPermission(Long groupId) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        GroupMember member = getMember(groupId, currentUserId);
        if (member == null || !member.isAdmin()) {
            throw new ServiceException("需要管理员权限");
        }
    }

    private void checkOwnerPermission(Long groupId) {
        Long currentUserId = LoginHelper.getUserId();
        if (currentUserId == null) {
            throw new ServiceException("请先登录");
        }

        GroupMember member = getMember(groupId, currentUserId);
        if (member == null || !member.isOwner()) {
            throw new ServiceException("需要群主权限");
        }
    }
    
    private void notifyUser(Long userId, String type, String message, Long groupId) {
        if (webSocketHandler.isOnline(userId)) {
            String json = JSONUtil.toJsonStr(Map.of(
                    "type", type,
                    "message", message,
                    "groupId", groupId
            ));
            webSocketHandler.sendToUser(userId, json);
        }
    }
}
