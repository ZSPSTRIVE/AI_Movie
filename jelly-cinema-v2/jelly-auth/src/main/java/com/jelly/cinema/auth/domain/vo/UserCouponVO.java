package com.jelly.cinema.auth.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserCouponVO {

    private Long id;

    private Long templateId;

    private String title;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private LocalDateTime useTime;
}
