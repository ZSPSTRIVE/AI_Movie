package com.jelly.cinema.admin.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 举报记录 VO
 *
 * @author Jelly Cinema
 */
@Data
public class ReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 举报人ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reporterId;

    /**
     * 举报人昵称
     */
    private String reporterNickname;

    /**
     * 举报人头像
     */
    private String reporterAvatar;

    /**
     * 被举报ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;

    /**
     * 目标类型：1-用户, 2-群组, 3-消息, 4-帖子
     */
    private Integer targetType;

    /**
     * 被举报对象名称
     */
    private String targetName;

    /**
     * 被举报对象头像
     */
    private String targetAvatar;

    /**
     * 举报原因
     */
    private String reason;

    /**
     * 详细描述
     */
    private String description;

    /**
     * 截图证据URL列表
     */
    private List<String> evidenceImgs;

    /**
     * 状态：0-待处理, 1-已处理, 2-已忽略
     */
    private Integer status;

    /**
     * 处理结果
     */
    private String result;

    /**
     * 处理人名称
     */
    private String handlerName;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 举报时间
     */
    private LocalDateTime createTime;
}
