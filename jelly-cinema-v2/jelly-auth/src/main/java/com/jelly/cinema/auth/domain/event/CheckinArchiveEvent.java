package com.jelly.cinema.auth.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinArchiveEvent {

    private Long userId;

    private LocalDate checkinDate;

    private LocalDateTime eventTime;
}
