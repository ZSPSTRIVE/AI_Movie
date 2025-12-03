package com.jelly.cinema.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员操作日志实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_admin_log")
public class AdminLog extends BaseEntity {

    /**
     * 管理员ID
     */
    private Long adminId;

    /**
     * 管理员名称
     */
    private String adminName;

    /**
     * 模块：用户/群组/内容
     */
    private String module;

    /**
     * 动作：封禁/解封/解散/删除
     */
    private String action;

    /**
     * 操作对象ID
     */
    private Long targetId;

    /**
     * 对象类型：user/group/post
     */
    private String targetType;

    /**
     * 操作详情/理由
     */
    private String detail;

    /**
     * 操作IP
     */
    private String ip;

    // ========== 模块常量 ==========
    public static final String MODULE_USER = "用户";
    public static final String MODULE_GROUP = "群组";
    public static final String MODULE_CONTENT = "内容";

    // ========== 动作常量 ==========
    public static final String ACTION_BAN = "封禁";
    public static final String ACTION_UNBAN = "解封";
    public static final String ACTION_DISMISS = "解散";
    public static final String ACTION_DELETE = "删除";
    public static final String ACTION_RESET_PWD = "重置密码";
}
