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
import com.jelly.cinema.common.captcha.constant.CaptchaConstants;
import com.jelly.cinema.common.captcha.service.CaptchaService;
import com.jelly.cinema.common.captcha.service.EmailService;
import com.jelly.cinema.common.core.domain.model.LoginUser;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.core.utils.SecurityUtils;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.common.security.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

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
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final RedisService redisService;

    /**
     * 登录失败次数缓存 Key 前缀
     */
    private static final String LOGIN_FAIL_COUNT_KEY = "jelly:auth:login:fail:";

    /**
     * 触发邮箱验证的失败次数阈值
     */
    private static final int EMAIL_VERIFY_THRESHOLD = 3;

    /**
     * 登录失败记录过期时间（小时）
     */
    private static final int LOGIN_FAIL_EXPIRE_HOURS = 24;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 验证图片验证码
        captchaService.checkCaptcha(dto.getCaptchaKey(), dto.getCaptcha());

        // 2. 查询用户（支持用户名或邮箱登录）
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
                        .or()
                        .eq(User::getEmail, dto.getUsername())
        );
        
        if (user == null) {
            throw new ServiceException(401, "用户名或密码错误");
        }

        // 3. 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new ServiceException(403, "用户已被禁用");
        }

        // 4. 检查是否需要邮箱二次验证
        String failCountKey = LOGIN_FAIL_COUNT_KEY + user.getUsername();
        Integer failCount = redisService.get(failCountKey);
        
        if (failCount != null && failCount >= EMAIL_VERIFY_THRESHOLD) {
            // 需要邮箱验证
            if (StrUtil.isBlank(dto.getEmailCode())) {
                throw new ServiceException(4011, "登录异常，请输入邮箱验证码");
            }
            // 验证邮箱验证码
            emailService.checkCode(user.getEmail(), dto.getEmailCode(), CaptchaConstants.BusinessType.LOGIN);
        }

        // 5. 验证密码
        try {
            SecurityUtils.checkPassword(dto.getPassword(), user.getPassword());
        } catch (Exception e) {
            // 密码错误，增加失败次数
            incrementLoginFailCount(user.getUsername());
            throw new ServiceException(401, "用户名或密码错误");
        }

        // 6. 登录成功，清除失败次数
        redisService.delete(failCountKey);

        // 7. 构建登录用户信息
        LoginUser loginUser = new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getRole()
        );

        // 8. 执行登录
        LoginHelper.login(loginUser);

        log.info("用户登录成功: {}", user.getUsername());

        // 9. 返回登录结果
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
        // 1. 校验密码一致性
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new ServiceException("两次输入的密码不一致");
        }

        // 2. 验证邮箱验证码
        emailService.checkCode(dto.getEmail(), dto.getEmailCode(), CaptchaConstants.BusinessType.REGISTER);

        // 3. 检查用户名是否已存在
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, dto.getUsername())
        );
        if (usernameCount > 0) {
            throw new ServiceException("用户名已存在");
        }

        // 4. 检查邮箱是否已存在
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, dto.getEmail())
        );
        if (emailCount > 0) {
            throw new ServiceException("该邮箱已被注册");
        }

        // 5. 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(SecurityUtils.encryptPassword(dto.getPassword()));
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setRole("ROLE_USER");
        user.setStatus(0);
        user.setAvatar("https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png");

        userMapper.insert(user);
        log.info("用户注册成功: username={}, email={}", user.getUsername(), user.getEmail());
    }

    @Override
    public void logout() {
        LoginHelper.logout();
    }

    @Override
    public boolean needEmailVerification(String username) {
        String failCountKey = LOGIN_FAIL_COUNT_KEY + username;
        Integer failCount = redisService.get(failCountKey);
        return failCount != null && failCount >= EMAIL_VERIFY_THRESHOLD;
    }

    @Override
    public String getMaskedEmail(String username) {
        String email = getFullEmail(username);
        return email != null ? maskEmail(email) : null;
    }

    @Override
    public String getFullEmail(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .or()
                        .eq(User::getEmail, username)
                        .select(User::getEmail)
        );
        
        if (user == null || StrUtil.isBlank(user.getEmail())) {
            return null;
        }
        
        return user.getEmail();
    }

    /**
     * 增加登录失败次数
     */
    private void incrementLoginFailCount(String username) {
        String failCountKey = LOGIN_FAIL_COUNT_KEY + username;
        if (Boolean.TRUE.equals(redisService.hasKey(failCountKey))) {
            redisService.increment(failCountKey);
        } else {
            redisService.set(failCountKey, 1, LOGIN_FAIL_EXPIRE_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * 隐藏邮箱中间部分
     * 例如: test@example.com -> te***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        int atIndex = email.indexOf("@");
        String prefix = email.substring(0, atIndex);
        String suffix = email.substring(atIndex);
        
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + suffix;
        } else {
            return prefix.substring(0, 2) + "***" + suffix;
        }
    }
}
