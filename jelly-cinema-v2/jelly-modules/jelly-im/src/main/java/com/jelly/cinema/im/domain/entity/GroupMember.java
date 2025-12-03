package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 群成员实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_group_member")
public class GroupMember extends BaseEntity {

    /**
     * 群ID
     */
    private Long groupId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色：0-普通成员, 1-管理员, 2-群主
     */
    private Integer role;

    /**
     * 群名片
     */
    private String groupNick;

    /**
     * 禁言结束时间（NULL代表未禁言）
     */
    private LocalDateTime muteEndTime;

    /**
     * 入群时间
     */
    private LocalDateTime joinTime;

    // ========== 角色常量 ==========
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;

    /**
     * 是否为管理层（管理员或群主）
     */
    public boolean isAdmin() {
        return role != null && role >= ROLE_ADMIN;
    }

    /**
     * 是否为群主
     */
    public boolean isOwner() {
        return role != null && role == ROLE_OWNER;
    }

    /**
     * 是否被禁言中
     */
    public boolean isMuted() {
        return muteEndTime != null && muteEndTime.isAfter(LocalDateTime.now());
    }
}
