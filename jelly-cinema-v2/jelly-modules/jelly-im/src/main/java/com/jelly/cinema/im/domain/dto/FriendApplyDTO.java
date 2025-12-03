package com.jelly.cinema.im.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 好友申请 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class FriendApplyDTO {

    /**
     * 目标用户ID
     */
    @NotNull(message = "目标用户ID不能为空")
    private Long targetId;

    /**
     * 验证消息
     */
    private String reason;

    /**
     * 备注名
     */
    private String remark;
}
