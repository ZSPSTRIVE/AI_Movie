package com.jelly.cinema.im.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 群组搜索结果 VO
 *
 * @author Jelly Cinema
 */
@Data
public class GroupSearchVO implements Serializable {

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
     * 是否已加入
     */
    private Boolean isJoined;
}
