package com.jelly.cinema.common.api.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 远程群组简要信息（用于用户详情展示）
 *
 * @author Jelly Cinema
 */
@Data
public class RemoteGroupSimple implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private Long id;

    /**
     * 群名称
     */
    private String name;

    /**
     * 群头像
     */
    private String avatar;

    /**
     * 成员数量
     */
    private Integer memberCount;
}
