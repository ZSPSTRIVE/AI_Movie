package com.jelly.cinema.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.UserDetailVO;
import com.jelly.cinema.admin.domain.vo.UserListVO;

/**
 * 用户管理服务
 *
 * @author Jelly Cinema
 */
public interface UserManageService {

    /**
     * 分页查询用户列表
     */
    Page<UserListVO> page(int pageNum, int pageSize, String keyword, Integer status);

    /**
     * 获取用户详情
     */
    UserDetailVO getDetail(Long userId);

    /**
     * 封禁用户
     *
     * @param userId   用户ID
     * @param duration 时长（小时），0表示永久
     * @param reason   封禁原因
     */
    void banUser(Long userId, int duration, String reason);

    /**
     * 解封用户
     */
    void unbanUser(Long userId);

    /**
     * 重置用户密码
     */
    String resetPassword(Long userId);

    /**
     * 强制下线
     */
    void forceLogout(Long userId);

    /**
     * 创建用户
     */
    Long createUser(String username, String password, String nickname, String role);

    /**
     * 更新用户信息
     */
    void updateUser(Long userId, String nickname, String email, String phone, String role);

    /**
     * 删除用户
     */
    void deleteUser(Long userId);
}
