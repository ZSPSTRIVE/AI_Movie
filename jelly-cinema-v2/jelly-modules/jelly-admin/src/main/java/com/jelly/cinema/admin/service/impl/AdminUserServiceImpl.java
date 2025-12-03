package com.jelly.cinema.admin.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.dto.UserQueryDTO;
import com.jelly.cinema.admin.domain.entity.User;
import com.jelly.cinema.admin.mapper.AdminUserMapper;
import com.jelly.cinema.admin.service.AdminUserService;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper userMapper;

    @Override
    public PageResult<User> list(UserQueryDTO query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getUsername()), User::getUsername, query.getUsername());
        wrapper.like(StrUtil.isNotBlank(query.getNickname()), User::getNickname, query.getNickname());
        wrapper.eq(StrUtil.isNotBlank(query.getRole()), User::getRole, query.getRole());
        wrapper.eq(query.getStatus() != null, User::getStatus, query.getStatus());
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> page = userMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 清除密码字段
        List<User> users = page.getRecords().stream()
                .peek(u -> u.setPassword(null))
                .collect(Collectors.toList());

        return PageResult.build(users, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        
        if ("ROLE_ADMIN".equals(user.getRole())) {
            throw new ServiceException("不能禁用管理员账号");
        }

        user.setStatus(status);
        userMapper.updateById(user);
        log.info("用户状态更新: id={}, status={}", id, status);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
        log.info("用户密码重置: id={}", id);
    }

    @Override
    public void delete(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        
        if ("ROLE_ADMIN".equals(user.getRole())) {
            throw new ServiceException("不能删除管理员账号");
        }

        userMapper.deleteById(id);
        log.info("用户删除: id={}", id);
    }

    @Override
    public Long countTotal() {
        return userMapper.countTotal();
    }

    @Override
    public Long countTodayNew() {
        return userMapper.countTodayNew();
    }
}
