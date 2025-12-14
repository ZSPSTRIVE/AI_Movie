package com.jelly.cinema.auth.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PointLogVO {

    private Long id;

    private Integer type;

    private String typeName;

    private Integer amount;

    private String remark;

    private LocalDateTime createTime;
}
