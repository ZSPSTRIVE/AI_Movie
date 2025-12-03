package com.jelly.cinema.admin.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群组审计 VO
 *
 * @author Jelly Cinema
 */
@Data
public class GroupAuditVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String groupNo;

    private String name;

    private String avatar;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;

    private String ownerNickname;

    private Integer memberCount;

    private Integer maxMember;

    private Integer status;

    private LocalDateTime createTime;
}
