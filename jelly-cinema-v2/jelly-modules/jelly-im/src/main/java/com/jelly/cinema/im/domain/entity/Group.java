package com.jelly.cinema.im.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jelly.cinema.common.mybatis.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 群组实体
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_group")
public class Group extends BaseEntity {

    /**
     * 群号（6-8位，用于搜索展示）
     */
    private String groupNo;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群头像URL
     */
    private String avatar;

    /**
     * 群简介
     */
    private String description;

    /**
     * 群公告
     */
    private String notice;

    /**
     * 群主ID
     */
    private Long ownerId;

    /**
     * 群成员上限
     */
    private Integer maxMember;

    /**
     * 当前成员数
     */
    private Integer memberCount;

    /**
     * 加群方式：0-自由加入, 1-需验证, 2-禁止加入
     */
    private Integer joinType;

    /**
     * 全员禁言：0-否, 1-是
     */
    private Integer isMuteAll;

    /**
     * 状态：0-正常，1-解散
     */
    private Integer status;
}
