package com.jelly.cinema.auth.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CouponTemplateVO {

    private Long id;

    private String title;

    private Integer totalCount;

    private Integer usedCount;

    private Integer pointsRequired;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private Integer remainStock;
}
