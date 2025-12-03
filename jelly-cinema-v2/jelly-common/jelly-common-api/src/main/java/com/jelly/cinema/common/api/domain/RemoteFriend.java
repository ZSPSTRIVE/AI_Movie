package com.jelly.cinema.common.api.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 远程好友信息
 *
 * @author Jelly Cinema
 */
@Data
public class RemoteFriend implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 好友ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 好友昵称
     */
    private String nickname;

    /**
     * 好友头像
     */
    private String avatar;

    /**
     * 备注名
     */
    private String remark;
}
