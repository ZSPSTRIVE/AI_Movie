package com.jelly.cinema.im.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 举报请求 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class ReportDTO {

    /**
     * 被举报对象ID
     */
    @NotNull(message = "被举报对象不能为空")
    private Long targetId;

    /**
     * 举报类型：1-用户 2-群组 3-消息 4-帖子
     */
    @NotNull(message = "举报类型不能为空")
    private Integer targetType;

    /**
     * 举报原因
     */
    @NotBlank(message = "举报原因不能为空")
    private String reason;

    /**
     * 证据图片URL列表
     */
    private List<String> evidenceImgs;
}
