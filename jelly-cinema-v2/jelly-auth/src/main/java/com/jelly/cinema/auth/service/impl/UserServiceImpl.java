package com.jelly.cinema.auth.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.jelly.cinema.auth.domain.entity.User;
import com.jelly.cinema.auth.domain.vo.UserVO;
import com.jelly.cinema.auth.mapper.UserMapper;
import com.jelly.cinema.auth.service.UserService;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return null;
        }
        return toVO(user);
    }

    @Override
    public List<UserVO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public void updateProfile(UserVO userVO) {
        Long userId = LoginHelper.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        // 只更新允许修改的字段
        if (userVO.getNickname() != null) {
            user.setNickname(userVO.getNickname());
        }
        if (userVO.getSignature() != null) {
            user.setSignature(userVO.getSignature());
        }
        if (userVO.getEmail() != null) {
            user.setEmail(userVO.getEmail());
        }
        if (userVO.getPhone() != null) {
            user.setPhone(userVO.getPhone());
        }

        userMapper.updateById(user);
        log.info("用户资料更新: userId={}", userId);
    }

    @Override
    public String updateAvatar(String avatar) {
        Long userId = LoginHelper.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        user.setAvatar(avatar);
        userMapper.updateById(user);
        log.info("用户头像更新: userId={}", userId);
        return avatar;
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        // 数字关键词：既匹配 ID，也支持昵称/用户名模糊匹配（避免数字用户名无法搜索）
        Long userId = null;
        try {
            userId = Long.parseLong(keyword);
        } catch (NumberFormatException e) {
            // ignore
        }

        if (userId != null) {
            final Long userIdFinal = userId;
            wrapper.and(w -> w.eq(User::getId, userIdFinal)
                    .or()
                    .like(User::getNickname, keyword)
                    .or()
                    .like(User::getUsername, keyword));
        } else {
            // 按昵称或用户名模糊匹配
            wrapper.and(w -> w.like(User::getNickname, keyword)
                    .or()
                    .like(User::getUsername, keyword));
        }

        // 只搜索正常状态的用户，限制结果数量（兼容历史数据 status 为空的情况）
        wrapper.and(w -> w.eq(User::getStatus, 0)
                .or()
                .isNull(User::getStatus))
               .last("LIMIT 20");

        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 转换为 VO
     */
    private UserVO toVO(User user) {
        UserVO vo = BeanUtil.copyProperties(user, UserVO.class);
        // 不返回敏感信息
        return vo;
    }
}
