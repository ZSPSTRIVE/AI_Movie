package com.jelly.cinema.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.im.domain.entity.UserSetting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户设置 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface UserSettingMapper extends BaseMapper<UserSetting> {
}
