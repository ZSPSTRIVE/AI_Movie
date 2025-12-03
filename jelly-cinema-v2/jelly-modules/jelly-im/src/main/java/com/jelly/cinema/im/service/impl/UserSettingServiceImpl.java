package com.jelly.cinema.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jelly.cinema.im.domain.dto.UserSettingDTO;
import com.jelly.cinema.im.domain.entity.UserSetting;
import com.jelly.cinema.im.mapper.UserSettingMapper;
import com.jelly.cinema.im.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户设置服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingServiceImpl implements UserSettingService {

    private final UserSettingMapper userSettingMapper;

    @Override
    public UserSetting getUserSetting(Long userId) {
        LambdaQueryWrapper<UserSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSetting::getUserId, userId);
        UserSetting setting = userSettingMapper.selectOne(wrapper);
        
        // 如果不存在，创建默认设置
        if (setting == null) {
            setting = createDefaultSetting(userId);
        }
        
        return setting;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserSetting(Long userId, UserSettingDTO dto) {
        LambdaQueryWrapper<UserSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserSetting::getUserId, userId);
        UserSetting setting = userSettingMapper.selectOne(wrapper);
        
        if (setting == null) {
            // 不存在则创建
            setting = createDefaultSetting(userId);
        }
        
        // 更新设置
        if (dto.getEnableNotification() != null) {
            setting.setEnableNotification(dto.getEnableNotification() ? 1 : 0);
        }
        if (dto.getEnableSound() != null) {
            setting.setEnableSound(dto.getEnableSound() ? 1 : 0);
        }
        if (dto.getShowOnlineStatus() != null) {
            setting.setShowOnlineStatus(dto.getShowOnlineStatus() ? 1 : 0);
        }
        if (dto.getAllowStrangerMsg() != null) {
            setting.setAllowStrangerMsg(dto.getAllowStrangerMsg() ? 1 : 0);
        }
        if (dto.getEnterToSend() != null) {
            setting.setEnterToSend(dto.getEnterToSend() ? 1 : 0);
        }
        if (dto.getShowReadStatus() != null) {
            setting.setShowReadStatus(dto.getShowReadStatus() ? 1 : 0);
        }
        
        userSettingMapper.updateById(setting);
        log.info("用户设置已更新: userId={}", userId);
    }

    /**
     * 创建默认设置
     */
    private UserSetting createDefaultSetting(Long userId) {
        UserSetting setting = new UserSetting();
        setting.setUserId(userId);
        setting.setEnableNotification(1);
        setting.setEnableSound(1);
        setting.setShowOnlineStatus(1);
        setting.setAllowStrangerMsg(0);
        setting.setEnterToSend(1);
        setting.setShowReadStatus(1);
        userSettingMapper.insert(setting);
        log.info("已创建默认用户设置: userId={}", userId);
        return setting;
    }
}
