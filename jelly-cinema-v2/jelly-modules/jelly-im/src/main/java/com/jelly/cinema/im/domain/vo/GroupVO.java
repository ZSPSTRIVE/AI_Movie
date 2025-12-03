package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 群组详情 VO
 *
 * @author Jelly Cinema
 */
@Data
public class GroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
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
     * 群公告
     */
    private String notice;

    /**
     * 群主ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    /**
     * 群主昵称
     */
    private String ownerNickname;

    /**
     * 当前成员数
     */
    private Integer memberCount;

    /**
     * 群成员上限
     */
    private Integer maxMember;

    /**
     * 加群方式：0-自由加入, 1-需验证, 2-禁止加入
     */
    private Integer joinType;

    /**
     * 全员禁言：0-否, 1-是
     */
    private Integer isMuteAll;

    /**
     * 我的群角色：0-普通成员, 1-管理员, 2-群主
     */
    private Integer myRole;

    /**
     * 我的群名片
     */
    private String myGroupNick;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 成员列表（前15个）
     */
    private List<GroupMemberVO> members;
}
