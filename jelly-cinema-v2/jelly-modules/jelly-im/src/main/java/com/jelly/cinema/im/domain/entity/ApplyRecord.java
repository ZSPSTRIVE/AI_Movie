package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 申请记录实体（好友申请、入群申请）
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_apply_record")
public class ApplyRecord extends BaseEntity {

    /**
     * 类型：1-好友申请, 2-入群申请
     */
    private Integer type;

    /**
     * 申请人ID
     */
    private Long fromId;

    /**
     * 目标ID（好友申请为用户ID，入群申请为群ID）
     */
    private Long targetId;

    /**
     * 验证消息/申请理由
     */
    private String reason;

    /**
     * 备注名（好友申请时）
     */
    private String remark;

    /**
     * 状态：0-待处理, 1-已同意, 2-已拒绝, 3-已忽略
     */
    private Integer status;

    /**
     * 处理人ID（入群申请时为管理员ID）
     */
    private Long handlerId;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    // ========== 类型常量 ==========
    public static final int TYPE_FRIEND = 1;
    public static final int TYPE_GROUP = 2;

    // ========== 状态常量 ==========
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_IGNORED = 3;
}
