package com.jelly.cinema.admin.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.AdminLog;
import com.jelly.cinema.admin.domain.entity.LoginLog;
import com.jelly.cinema.admin.domain.entity.UserBan;
import com.jelly.cinema.admin.domain.vo.UserDetailVO;
import com.jelly.cinema.admin.domain.vo.UserListVO;
import com.jelly.cinema.admin.mapper.AdminLogMapper;
import com.jelly.cinema.admin.mapper.AdminUserMapper;
import com.jelly.cinema.admin.mapper.LoginLogMapper;
import com.jelly.cinema.admin.mapper.UserBanMapper;
import com.jelly.cinema.admin.service.UserManageService;
import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.api.domain.RemoteGroupSimple;
import com.jelly.cinema.common.api.feign.RemoteImService;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户管理服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageServiceImpl implements UserManageService {

    private final AdminUserMapper userMapper;
    private final UserBanMapper userBanMapper;
    private final AdminLogMapper adminLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final RedisService redisService;
    private final RemoteImService remoteImService;

    private static final String USER_TOKEN_KEY = "sa-token:token:";

    @Override
    public Page<UserListVO> page(int pageNum, int pageSize, String keyword, Integer status) {
        Page<UserListVO> page = new Page<>(pageNum, pageSize);
        return userMapper.selectUserPage(page, keyword, status);
    }

    @Override
    public UserDetailVO getDetail(Long userId) {
        UserDetailVO detail = userMapper.selectUserDetail(userId);
        if (detail == null) {
            throw new ServiceException("用户不存在");
        }
        
        // 查询封禁状态
        LambdaQueryWrapper<UserBan> banWrapper = new LambdaQueryWrapper<>();
        banWrapper.eq(UserBan::getUserId, userId)
                .eq(UserBan::getStatus, UserBan.STATUS_ACTIVE)
                .orderByDesc(UserBan::getCreateTime)
                .last("LIMIT 1");
        UserBan ban = userBanMapper.selectOne(banWrapper);
        if (ban != null) {
            detail.setStatus(1);
            detail.setBanReason(ban.getReason());
            detail.setBanExpireTime(ban.getExpireTime());
        }
        
        // 查询好友列表（通过 IM 服务）
        try {
            R<List<RemoteFriend>> friendsResult = remoteImService.getUserFriends(userId);
            if (friendsResult.isSuccess() && friendsResult.getData() != null) {
                List<UserDetailVO.FriendItem> friends = friendsResult.getData().stream()
                        .map(rf -> {
                            UserDetailVO.FriendItem item = new UserDetailVO.FriendItem();
                            item.setId(rf.getId());
                            item.setNickname(rf.getNickname());
                            item.setAvatar(rf.getAvatar());
                            return item;
                        }).toList();
                detail.setFriends(friends);
            } else {
                detail.setFriends(new ArrayList<>());
            }
        } catch (Exception e) {
            log.warn("获取用户好友列表失败: {}", e.getMessage());
            detail.setFriends(new ArrayList<>());
        }
        
        // 查询群组列表（通过 IM 服务）
        try {
            R<List<RemoteGroupSimple>> groupsResult = remoteImService.getUserGroups(userId);
            if (groupsResult.isSuccess() && groupsResult.getData() != null) {
                List<UserDetailVO.GroupItem> groups = groupsResult.getData().stream()
                        .map(rg -> {
                            UserDetailVO.GroupItem item = new UserDetailVO.GroupItem();
                            item.setId(rg.getId());
                            item.setName(rg.getName());
                            item.setAvatar(rg.getAvatar());
                            item.setMemberCount(rg.getMemberCount());
                            return item;
                        }).toList();
                detail.setGroups(groups);
            } else {
                detail.setGroups(new ArrayList<>());
            }
        } catch (Exception e) {
            log.warn("获取用户群组列表失败: {}", e.getMessage());
            detail.setGroups(new ArrayList<>());
        }
        
        // 查询登录日志（最近10条）
        List<LoginLog> logs = loginLogMapper.selectRecentLogs(userId, 10);
        List<UserDetailVO.LoginLogItem> loginLogs = logs.stream()
                .map(log -> {
                    UserDetailVO.LoginLogItem item = new UserDetailVO.LoginLogItem();
                    item.setIp(log.getLoginIp());
                    item.setLocation(log.getLoginLocation());
                    item.setLoginTime(log.getLoginTime());
                    return item;
                }).toList();
        detail.setLoginLogs(loginLogs);
        
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(Long userId, int duration, String reason) {
        Long operatorId = LoginHelper.getUserId();
        
        // 创建封禁记录
        UserBan ban = new UserBan();
        ban.setUserId(userId);
        ban.setBanType(UserBan.TYPE_ALL);
        ban.setDuration(duration);
        ban.setReason(reason);
        ban.setOperatorId(operatorId);
        ban.setStatus(UserBan.STATUS_ACTIVE);
        
        if (duration > 0) {
            ban.setExpireTime(LocalDateTime.now().plusHours(duration));
        }
        
        userBanMapper.insert(ban);
        
        // 更新用户状态
        userMapper.updateStatus(userId, 1);
        
        // 强制下线
        forceLogout(userId);
        
        // 记录操作日志
        AdminLog adminLog = new AdminLog();
        adminLog.setAdminId(operatorId);
        adminLog.setModule(AdminLog.MODULE_USER);
        adminLog.setAction(AdminLog.ACTION_BAN);
        adminLog.setTargetId(userId);
        adminLog.setTargetType("user");
        adminLog.setDetail("时长: " + (duration > 0 ? duration + "小时" : "永久") + ", 原因: " + reason);
        adminLogMapper.insert(adminLog);
        
        log.info("用户 {} 已被封禁，时长: {}小时，原因: {}", userId, duration, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbanUser(Long userId) {
        Long operatorId = LoginHelper.getUserId();
        
        // 解除所有生效中的封禁
        LambdaQueryWrapper<UserBan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBan::getUserId, userId)
                .eq(UserBan::getStatus, UserBan.STATUS_ACTIVE);
        
        UserBan update = new UserBan();
        update.setStatus(UserBan.STATUS_INACTIVE);
        userBanMapper.update(update, wrapper);
        
        // 更新用户状态
        userMapper.updateStatus(userId, 0);
        
        // 记录操作日志
        AdminLog adminLog = new AdminLog();
        adminLog.setAdminId(operatorId);
        adminLog.setModule(AdminLog.MODULE_USER);
        adminLog.setAction(AdminLog.ACTION_UNBAN);
        adminLog.setTargetId(userId);
        adminLog.setTargetType("user");
        adminLogMapper.insert(adminLog);
        
        log.info("用户 {} 已解封", userId);
    }

    @Override
    public String resetPassword(Long userId) {
        Long operatorId = LoginHelper.getUserId();
        
        // 生成随机密码（8位，包含字母数字）
        String newPassword = RandomUtil.randomString("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 8);
        
        // 直接更新用户密码（BCrypt 加密）
        userMapper.updatePassword(userId, BCrypt.hashpw(newPassword));
        
        // 强制下线
        forceLogout(userId);
        
        // 记录操作日志
        AdminLog adminLog = new AdminLog();
        adminLog.setAdminId(operatorId);
        adminLog.setModule(AdminLog.MODULE_USER);
        adminLog.setAction(AdminLog.ACTION_RESET_PWD);
        adminLog.setTargetId(userId);
        adminLog.setTargetType("user");
        adminLogMapper.insert(adminLog);
        
        log.info("用户 {} 密码已重置", userId);
        return newPassword;
    }

    @Override
    public void forceLogout(Long userId) {
        // 删除用户的所有 Token
        redisService.deleteByPattern(USER_TOKEN_KEY + "*:" + userId);
        log.info("用户 {} 已强制下线", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(String username, String password, String nickname, String role) {
        // 检查用户名是否已存在
        if (userMapper.existsByUsername(username)) {
            throw new ServiceException("用户名已存在");
        }
        
        // 创建用户
        int rows = userMapper.insertUser(
                username,
                BCrypt.hashpw(password),
                nickname != null ? nickname : username,
                role != null ? role : "ROLE_USER"
        );
        
        if (rows <= 0) {
            throw new ServiceException("创建用户失败");
        }
        
        // 获取生成的用户ID
        Long userId = userMapper.getLastInsertId();
        
        log.info("创建用户成功: userId={}, username={}", userId, username);
        return userId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, String nickname, String email, String phone, String role) {
        if (!userMapper.existsById(userId)) {
            throw new ServiceException("用户不存在");
        }
        
        userMapper.updateUserInfo(userId, nickname, email, phone, role);
        log.info("更新用户信息: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        if (!userMapper.existsById(userId)) {
            throw new ServiceException("用户不存在");
        }
        
        // 软删除
        userMapper.deleteById(userId);
        
        // 强制下线
        forceLogout(userId);
        
        log.info("删除用户: userId={}", userId);
    }
}
