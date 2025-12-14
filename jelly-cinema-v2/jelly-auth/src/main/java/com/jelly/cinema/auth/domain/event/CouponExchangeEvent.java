package com.jelly.cinema.auth.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponExchangeEvent {

    private Long userId;

    private Long templateId;

    private LocalDateTime expireTime;

    private LocalDateTime eventTime;
}
