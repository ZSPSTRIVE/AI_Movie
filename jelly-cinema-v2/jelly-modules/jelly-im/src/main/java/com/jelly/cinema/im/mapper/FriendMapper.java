package com.jelly.cinema.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.im.domain.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 好友关系 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    /**
     * 查询好友关系（包括已删除的）
     */
    @Select("SELECT * FROM t_friend WHERE user_id = #{userId} AND friend_id = #{friendId} LIMIT 1")
    Friend selectIncludeDeleted(@Param("userId") Long userId, @Param("friendId") Long friendId);

    /**
     * 恢复已删除的好友关系
     */
    @Update("UPDATE t_friend SET deleted = 0, status = 0, remark = #{remark}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int restoreFriend(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("remark") String remark);
}
