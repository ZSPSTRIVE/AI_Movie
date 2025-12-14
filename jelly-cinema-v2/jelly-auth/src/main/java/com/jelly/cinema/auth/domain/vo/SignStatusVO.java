package com.jelly.cinema.auth.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SignStatusVO {

    private Boolean signedToday;

    private Integer continuousDays;

    private Integer monthTotalDays;

    private List<Integer> signedDays;
}
