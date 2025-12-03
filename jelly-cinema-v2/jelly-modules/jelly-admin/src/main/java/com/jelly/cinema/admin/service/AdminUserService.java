package com.jelly.cinema.admin.service;

import com.jelly.cinema.admin.domain.dto.UserQueryDTO;
import com.jelly.cinema.admin.domain.entity.User;
import com.jelly.cinema.common.core.domain.PageResult;

/**
 * 用户管理服务接口
 *
 * @author Jelly Cinema
 */
public interface AdminUserService {

    /**
     * 分页查询用户
     */
    PageResult<User> list(UserQueryDTO query);

    /**
     * 获取用户详情
     */
    User getById(Long id);

    /**
     * 禁用/启用用户
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置密码
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 删除用户
     */
    void delete(Long id);

    /**
     * 统计用户数
     */
    Long countTotal();

    /**
     * 统计今日新增
     */
    Long countTodayNew();
}
