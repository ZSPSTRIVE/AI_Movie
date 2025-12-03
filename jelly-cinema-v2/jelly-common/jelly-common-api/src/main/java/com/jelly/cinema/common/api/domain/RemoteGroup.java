package com.jelly.cinema.common.api.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 远程群组信息
 *
 * @author Jelly Cinema
 */
@Data
public class RemoteGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private Long id;

    /**
     * 群号
     */
    private String groupNo;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群头像
     */
    private String avatar;

    /**
     * 群简介
     */
    private String description;

    /**
     * 群主ID
     */
    private Long ownerId;

    /**
     * 群主昵称
     */
    private String ownerNickname;

    /**
     * 成员数量
     */
    private Integer memberCount;

    /**
     * 最大成员数
     */
    private Integer maxMember;

    /**
     * 状态：0-正常，1-解散
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
