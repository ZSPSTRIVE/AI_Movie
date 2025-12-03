package com.jelly.cinema.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.entity.User;
import com.jelly.cinema.admin.domain.vo.UserDetailVO;
import com.jelly.cinema.admin.domain.vo.UserListVO;
import org.apache.ibatis.annotations.*;

/**
 * 用户 Mapper（管理端）
 *
 * @author Jelly Cinema
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<User> {

    /**
     * 统计用户总数
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0")
    Long countTotal();

    /**
     * 统计今日新增用户
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0 AND DATE(create_time) = CURDATE()")
    Long countTodayNew();

    /**
     * 分页查询用户列表
     */
    @Select("<script>" +
            "SELECT id, username, nickname, avatar, phone, email, status, create_time " +
            "FROM t_user WHERE deleted = 0 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (username LIKE CONCAT('%', #{keyword}, '%') OR nickname LIKE CONCAT('%', #{keyword}, '%') OR phone LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "<if test='status != null'>" +
            "AND status = #{status} " +
            "</if>" +
            "ORDER BY create_time DESC" +
            "</script>")
    Page<UserListVO> selectUserPage(Page<UserListVO> page, @Param("keyword") String keyword, @Param("status") Integer status);

    /**
     * 查询用户详情
     */
    @Select("SELECT id, username, nickname, avatar, signature, phone, email, status, create_time " +
            "FROM t_user WHERE id = #{userId} AND deleted = 0")
    UserDetailVO selectUserDetail(@Param("userId") Long userId);

    /**
     * 更新用户状态
     */
    @Update("UPDATE t_user SET status = #{status} WHERE id = #{userId}")
    void updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 更新用户密码
     */
    @Update("UPDATE t_user SET password = #{password} WHERE id = #{userId}")
    void updatePassword(@Param("userId") Long userId, @Param("password") String password);

    /**
     * 统计指定日期的新增用户数
     */
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted = 0 AND DATE(create_time) = #{date}")
    Long countByDate(@Param("date") String date);

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM t_user WHERE username = #{username} AND deleted = 0")
    boolean existsByUsername(@Param("username") String username);

    /**
     * 检查用户是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM t_user WHERE id = #{userId} AND deleted = 0")
    boolean existsById(@Param("userId") Long userId);

    /**
     * 创建用户
     */
    @Insert("INSERT INTO t_user (username, password, nickname, role, status, avatar, create_time) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{role}, 0, " +
            "'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png', NOW())")
    int insertUser(@Param("username") String username, @Param("password") String password,
                   @Param("nickname") String nickname, @Param("role") String role);

    /**
     * 获取最后插入的ID
     */
    @Select("SELECT LAST_INSERT_ID()")
    Long getLastInsertId();

    /**
     * 更新用户信息
     */
    @Update("<script>" +
            "UPDATE t_user SET " +
            "<if test='nickname != null'>nickname = #{nickname},</if>" +
            "<if test='email != null'>email = #{email},</if>" +
            "<if test='phone != null'>phone = #{phone},</if>" +
            "<if test='role != null'>role = #{role},</if>" +
            "update_time = NOW() " +
            "WHERE id = #{userId}" +
            "</script>")
    void updateUserInfo(@Param("userId") Long userId, @Param("nickname") String nickname,
                        @Param("email") String email, @Param("phone") String phone, @Param("role") String role);
}
