package com.jelly.cinema.common.security.utils;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.jelly.cinema.common.core.domain.model.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录工具类
 * 
 * 支持两种模式：
 * 1. 认证服务模式：使用 Sa-Token Session 存储用户信息
 * 2. 微服务模式：从 Gateway 传递的请求头获取用户信息
 *
 * @author Jelly Cinema
 */
public class LoginHelper {

    private static final String LOGIN_USER_KEY = "loginUser";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USERNAME = "X-Username";

    private LoginHelper() {
    }

    /**
     * 登录并存储用户信息（仅在 Auth 服务使用）
     *
     * @param loginUser 登录用户
     */
    public static void login(LoginUser loginUser) {
        StpUtil.login(loginUser.getUserId());
        setLoginUser(loginUser);
    }

    /**
     * 获取当前登录用户
     * 优先从请求头获取（微服务模式），否则从 Sa-Token Session 获取
     *
     * @return 登录用户信息
     */
    public static LoginUser getLoginUser() {
        // 尝试从请求头获取（Gateway 传递）
        Long userId = getUserIdFromHeader();
        if (userId != null) {
            LoginUser loginUser = new LoginUser();
            loginUser.setUserId(userId);
            loginUser.setUsername(getUsernameFromHeader());
            return loginUser;
        }
        
        // 回退到 Sa-Token Session（Auth 服务）
        try {
            if (StpUtil.isLogin()) {
                SaSession session = StpUtil.getSession();
                return (LoginUser) session.get(LOGIN_USER_KEY);
            }
        } catch (Exception ignored) {
            // 未登录或 Token 无效
        }
        return null;
    }

    /**
     * 设置登录用户信息
     *
     * @param loginUser 登录用户
     */
    public static void setLoginUser(LoginUser loginUser) {
        SaSession session = StpUtil.getSession();
        session.set(LOGIN_USER_KEY, loginUser);
        // 单独存储 role，供 Gateway 的 AdminAuthFilter 使用（避免跨模块反射问题）
        session.set("role", loginUser.getRole());
    }

    /**
     * 获取当前登录用户 ID
     * 优先从请求头获取
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        // 尝试从请求头获取
        Long userId = getUserIdFromHeader();
        if (userId != null) {
            return userId;
        }
        
        // 回退到 Sa-Token
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsLong();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 获取当前登录用户名
     *
     * @return 用户名
     */
    public static String getUsername() {
        // 尝试从请求头获取
        String username = getUsernameFromHeader();
        if (StrUtil.isNotBlank(username)) {
            return username;
        }
        
        // 回退到 LoginUser
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUsername() : null;
    }

    /**
     * 判断当前用户是否已登录
     *
     * @return 是否已登录
     */
    public static boolean isLogin() {
        // 先检查请求头
        if (getUserIdFromHeader() != null) {
            return true;
        }
        
        // 回退到 Sa-Token
        try {
            return StpUtil.isLogin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 退出登录
     */
    public static void logout() {
        try {
            StpUtil.logout();
        } catch (Exception ignored) {
        }
    }

    /**
     * 判断是否为管理员
     *
     * @return 是否为管理员
     */
    public static boolean isAdmin() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null && "ROLE_ADMIN".equals(loginUser.getRole());
    }
    
    /**
     * 从请求头获取用户 ID
     */
    private static Long getUserIdFromHeader() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            String userIdStr = request.getHeader(HEADER_USER_ID);
            if (StrUtil.isNotBlank(userIdStr)) {
                try {
                    return Long.parseLong(userIdStr);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }
    
    /**
     * 从请求头获取用户名
     */
    private static String getUsernameFromHeader() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            return request.getHeader(HEADER_USERNAME);
        }
        return null;
    }
    
    /**
     * 获取当前请求
     */
    private static HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
