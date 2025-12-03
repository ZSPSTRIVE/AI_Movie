package com.jelly.cinema.im.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 入群申请 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class GroupApplyDTO {

    /**
     * 群ID
     */
    @NotNull(message = "群ID不能为空")
    private Long groupId;

    /**
     * 申请理由
     */
    private String reason;
}
