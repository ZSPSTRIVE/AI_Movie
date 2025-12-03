package com.jelly.cinema.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户封禁记录实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_user_ban")
public class UserBan extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 封禁类型：1-登录封禁, 2-发言禁止, 3-全部
     */
    private Integer banType;

    /**
     * 封禁时长（小时），0表示永久
     */
    private Integer duration;

    /**
     * 封禁原因
     */
    private String reason;

    /**
     * 解封时间（NULL表示永久）
     */
    private LocalDateTime expireTime;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 状态：0-已解除, 1-生效中
     */
    private Integer status;

    // ========== 封禁类型常量 ==========
    public static final int TYPE_LOGIN = 1;
    public static final int TYPE_SPEAK = 2;
    public static final int TYPE_ALL = 3;

    // ========== 状态常量 ==========
    public static final int STATUS_INACTIVE = 0;
    public static final int STATUS_ACTIVE = 1;
}
