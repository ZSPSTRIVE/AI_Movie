package com.jelly.cinema.auth.service;

import com.jelly.cinema.auth.domain.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author Jelly Cinema
 */
public interface UserService {

    /**
     * 根据 ID 获取用户信息
     *
     * @param id 用户 ID
     * @return 用户信息
     */
    UserVO getUserById(Long id);

    /**
     * 批量获取用户信息
     *
     * @param ids 用户 ID 列表
     * @return 用户信息列表
     */
    List<UserVO> getUsersByIds(List<Long> ids);

    /**
     * 更新用户资料
     *
     * @param userVO 用户资料
     */
    void updateProfile(UserVO userVO);

    /**
     * 更新头像
     *
     * @param avatar 头像 URL
     * @return 新头像 URL
     */
    String updateAvatar(String avatar);

    /**
     * 搜索用户
     *
     * @param keyword 关键词（UID精确匹配或昵称模糊匹配）
     * @return 用户列表
     */
    List<UserVO> searchUsers(String keyword);
}
