package com.saferoute.domain.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// 훈련 종료 후 관리자가 입력하는 값.
public record GenerateReportRequest(
        @NotNull @PositiveOrZero Integer participantCount,
        @NotNull @PositiveOrZero Integer survivorCount
) {
}
