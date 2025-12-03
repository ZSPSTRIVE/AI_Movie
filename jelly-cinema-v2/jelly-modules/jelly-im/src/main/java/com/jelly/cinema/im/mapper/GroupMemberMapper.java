package com.jelly.cinema.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.im.domain.entity.GroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 群成员 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMember> {

    /**
     * 查询用户加入的所有群ID
     */
    @Select("SELECT group_id FROM t_group_member WHERE user_id = #{userId} AND deleted = 0")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询群的所有成员ID
     */
    @Select("SELECT user_id FROM t_group_member WHERE group_id = #{groupId} AND deleted = 0")
    List<Long> selectUserIdsByGroupId(@Param("groupId") Long groupId);
}
