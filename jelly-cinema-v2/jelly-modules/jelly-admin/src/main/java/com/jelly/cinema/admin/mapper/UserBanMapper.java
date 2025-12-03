package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户封禁记录 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface UserBanMapper extends BaseMapper<UserBan> {
}
