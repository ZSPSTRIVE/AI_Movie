package com.jelly.cinema.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.auth.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
