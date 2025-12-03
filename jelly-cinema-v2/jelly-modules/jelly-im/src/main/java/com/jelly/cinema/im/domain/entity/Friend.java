package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友关系实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_friend")
public class Friend extends BaseEntity {

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 好友 ID
     */
    private Long friendId;

    /**
     * 备注名
     */
    private String remark;

    /**
     * 好友分组（默认"我的好友"）
     * 注：暂未在数据库中创建该字段，标记为非数据库字段
     */
    @TableField(exist = false)
    private String groupName;

    /**
     * 状态：0-正常，1-拉黑
     */
    private Integer status;
}
