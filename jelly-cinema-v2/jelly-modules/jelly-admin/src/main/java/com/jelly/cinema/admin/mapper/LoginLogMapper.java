package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.admin.domain.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 登录日志 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /**
     * 查询用户最近登录日志
     */
    @Select("SELECT id, user_id, login_ip, login_location, login_time " +
            "FROM t_login_log WHERE user_id = #{userId} AND status = 1 " +
            "ORDER BY login_time DESC LIMIT #{limit}")
    List<LoginLog> selectRecentLogs(@Param("userId") Long userId, @Param("limit") int limit);
}
