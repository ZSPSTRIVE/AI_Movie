package com.jelly.cinema.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 举报记录实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_report")
public class Report extends BaseEntity {

    /**
     * 举报人ID
     */
    private Long reporterId;

    /**
     * 被举报ID（人/群/消息）
     */
    private Long targetId;

    /**
     * 类型：1-用户, 2-群组, 3-消息, 4-帖子
     */
    private Integer targetType;

    /**
     * 举报原因
     */
    private String reason;

    /**
     * 详细描述
     */
    private String description;

    /**
     * 截图证据URL（JSON数组）
     */
    private String evidenceImgs;

    /**
     * 状态：0-待处理, 1-已处理, 2-已忽略
     */
    private Integer status;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 处理人ID
     */
    private Long handlerId;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    // ========== 目标类型常量 ==========
    public static final int TARGET_USER = 1;
    public static final int TARGET_GROUP = 2;
    public static final int TARGET_MESSAGE = 3;
    public static final int TARGET_POST = 4;

    // ========== 状态常量 ==========
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_HANDLED = 1;
    public static final int STATUS_IGNORED = 2;
}
