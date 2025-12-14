package com.jelly.cinema.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.auth.domain.entity.UserPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserPointMapper extends BaseMapper<UserPoint> {

    @Update("UPDATE user_point SET points = points + #{delta}, version = version + 1 WHERE user_id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("delta") int delta);

    @Update("UPDATE user_point SET points = points - #{delta}, version = version + 1 WHERE user_id = #{userId} AND points >= #{delta}")
    int deductPoints(@Param("userId") Long userId, @Param("delta") int delta);
}
