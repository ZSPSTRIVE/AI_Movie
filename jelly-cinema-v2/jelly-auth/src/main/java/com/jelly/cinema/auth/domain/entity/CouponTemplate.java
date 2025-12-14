package com.jelly.cinema.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("coupon_template")
public class CouponTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private Integer totalCount;

    private Integer usedCount;

    private Integer pointsRequired;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;
}
