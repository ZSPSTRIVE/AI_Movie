package com.jelly.cinema.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.auth.domain.dto.LoginDTO;
import com.jelly.cinema.auth.domain.dto.RegisterDTO;
import com.jelly.cinema.auth.domain.entity.User;
import com.jelly.cinema.auth.domain.vo.LoginVO;
import com.jelly.cinema.auth.mapper.UserMapper;
import com.jelly.cinema.auth.service.AuthService;
import com.jelly.cinema.common.core.domain.model.LoginUser;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.core.utils.SecurityUtils;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        
        if (user == null) {
            throw new ServiceException(401, "用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new ServiceException(403, "用户已被禁用");
        }

        // 验证密码
        SecurityUtils.checkPassword(dto.getPassword(), user.getPassword());

        // 构建登录用户信息
        LoginUser loginUser = new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getRole()
        );

        // 执行登录
        LoginHelper.login(loginUser);

        // 返回登录结果
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .token(StpUtil.getTokenValue())
                .expireIn(StpUtil.getTokenTimeout())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto) {
        // 校验密码一致性
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new ServiceException("两次输入的密码不一致");
        }

        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new ServiceException("用户名已存在");
        }

        // 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(SecurityUtils.encryptPassword(dto.getPassword()));
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole("ROLE_USER");
        user.setStatus(0);
        user.setAvatar("https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png");

        userMapper.insert(user);
        log.info("用户注册成功: {}", user.getUsername());
    }

    @Override
    public void logout() {
        LoginHelper.logout();
    }
}
