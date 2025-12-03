package com.jelly.cinema.im.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 处理申请 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class ApplyHandleDTO {

    /**
     * 申请ID
     */
    @NotNull(message = "申请ID不能为空")
    private Long applyId;

    /**
     * 处理状态：1-同意, 2-拒绝
     */
    @NotNull(message = "处理状态不能为空")
    private Integer status;

    /**
     * 好友分组（同意好友申请时可选）
     */
    private String groupName;

    /**
     * 备注名（同意好友申请时可选）
     */
    private String remark;
}
