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
     * 登录
     *
     * @param dto 登录请求
     * @return 登录响应
     */
    LoginVO login(LoginDTO dto);

    /**
     * 注册
     *
     * @param dto 注册请求
     */
    void register(RegisterDTO dto);

    /**
     * 退出登录
     */
    void logout();
}
