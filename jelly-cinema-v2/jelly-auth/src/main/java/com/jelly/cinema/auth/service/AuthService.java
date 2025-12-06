package com.jelly.cinema.auth.service;

import com.jelly.cinema.auth.domain.dto.LoginDTO;
import com.jelly.cinema.auth.domain.dto.RegisterDTO;
import com.jelly.cinema.auth.domain.vo.LoginVO;

/**
 * 认证服务接口
 *
 * @author Jelly Cinema
 */
public interface AuthService {

    /**
     * 登录（包含图片验证码验证）
     *
     * @param dto 登录请求
     * @return 登录响应
     */
    LoginVO login(LoginDTO dto);

    /**
     * 注册（包含邮箱验证码验证）
     *
     * @param dto 注册请求
     */
    void register(RegisterDTO dto);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 检查是否需要邮箱二次验证
     *
     * @param username 用户名
     * @return 是否需要邮箱验证
     */
    boolean needEmailVerification(String username);

    /**
     * 获取用户邮箱（用于发送验证码）
     *
     * @param username 用户名
     * @return 邮箱地址（部分隐藏）
     */
    String getMaskedEmail(String username);

    /**
     * 获取用户完整邮箱
     *
     * @param username 用户名
     * @return 完整邮箱地址
     */
    String getFullEmail(String username);
}
