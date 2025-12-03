package com.jelly.cinema.im.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.im.domain.entity.Friend;
import com.jelly.cinema.im.domain.entity.Group;
import com.jelly.cinema.im.domain.entity.GroupMember;
import com.jelly.cinema.im.domain.vo.GroupSearchVO;
import com.jelly.cinema.im.domain.vo.UserSearchVO;
import com.jelly.cinema.im.mapper.FriendMapper;
import com.jelly.cinema.im.mapper.GroupMapper;
import com.jelly.cinema.im.mapper.GroupMemberMapper;
import com.jelly.cinema.im.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final RemoteUserService remoteUserService;
    private final FriendMapper friendMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;

    @Override
    public List<UserSearchVO> searchUser(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }

        // 调用 auth 服务搜索用户
        R<List<RemoteUser>> result = remoteUserService.searchUsers(keyword);
        if (!result.isSuccess() || result.getData() == null) {
            return Collections.emptyList();
        }

        Long currentUserId = LoginHelper.getUserId();
        
        // 获取当前用户的好友列表
        Set<Long> friendIds = Collections.emptySet();
        if (currentUserId != null) {
            LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Friend::getUserId, currentUserId);
            List<Friend> friends = friendMapper.selectList(wrapper);
            friendIds = friends.stream().map(Friend::getFriendId).collect(Collectors.toSet());
        }

        Set<Long> finalFriendIds = friendIds;
        return result.getData().stream()
                .filter(u -> !u.getId().equals(currentUserId)) // 排除自己
                .map(user -> {
                    UserSearchVO vo = new UserSearchVO();
                    vo.setId(user.getId());
                    vo.setUsername(user.getUsername());
                    vo.setNickname(user.getNickname());
                    vo.setAvatar(user.getAvatar());
                    vo.setSignature(user.getSignature());
                    vo.setIsFriend(finalFriendIds.contains(user.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<GroupSearchVO> searchGroup(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Group> wrapper = new LambdaQueryWrapper<>();
        
        // 尝试按群号精确匹配
        if (keyword.matches("\\d{6,8}")) {
            wrapper.eq(Group::getGroupNo, keyword);
        } else {
            // 按群名模糊匹配
            wrapper.like(Group::getName, keyword);
        }

        // 只搜索正常状态的群，限制结果数量
        wrapper.eq(Group::getStatus, 0)
               .last("LIMIT 20");

        List<Group> groups = groupMapper.selectList(wrapper);

        Long currentUserId = LoginHelper.getUserId();
        
        // 获取当前用户加入的群
        Set<Long> joinedGroupIds = Collections.emptySet();
        if (currentUserId != null) {
            LambdaQueryWrapper<GroupMember> memberWrapper = new LambdaQueryWrapper<>();
            memberWrapper.eq(GroupMember::getUserId, currentUserId);
            List<GroupMember> members = groupMemberMapper.selectList(memberWrapper);
            joinedGroupIds = members.stream().map(GroupMember::getGroupId).collect(Collectors.toSet());
        }

        Set<Long> finalJoinedGroupIds = joinedGroupIds;
        return groups.stream()
                .map(group -> {
                    GroupSearchVO vo = BeanUtil.copyProperties(group, GroupSearchVO.class);
                    vo.setIsJoined(finalJoinedGroupIds.contains(group.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
