package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.dto.UserSettingDTO;
import com.jelly.cinema.im.domain.entity.UserSetting;

/**
 * 用户设置服务接口
 *
 * @author Jelly Cinema
 */
public interface UserSettingService {

    /**
     * 获取用户设置
     *
     * @param userId 用户ID
     * @return 用户设置
     */
    UserSetting getUserSetting(Long userId);

    /**
     * 更新用户设置
     *
     * @param userId 用户ID
     * @param dto    设置DTO
     */
    void updateUserSetting(Long userId, UserSettingDTO dto);
}
