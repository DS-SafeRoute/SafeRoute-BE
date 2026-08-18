package com.saferoute.domain.congestion.dto.request;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReportCongestionRequest(
        @NotNull UUID eventId,
        @NotNull UUID edgeId,
        @NotBlank String cctvCode,
        @NotNull @PositiveOrZero Double avgHeadcount,
        @NotNull @PositiveOrZero Integer peakHeadcount,
        @NotNull @PositiveOrZero Integer sampleCount,
        @NotNull @PositiveOrZero Double density,
        @NotNull CongestionLevel congestionLevel,
        @NotNull @PositiveOrZero Long windowStart,
        @NotNull @PositiveOrZero Long windowEnd,
        @NotNull @PositiveOrZero Long capturedAt,
        @NotNull @PositiveOrZero Long configVersion,
        String monitoringImageKey
) {
    @AssertTrue(message = "avgHeadcount must not exceed peakHeadcount")
    public boolean isHeadcountValid() {
        if (avgHeadcount == null || peakHeadcount == null) {
            return true;
        }
        return avgHeadcount <= peakHeadcount.doubleValue();
    }

    @AssertTrue(message = "windowStart must be before windowEnd")
    public boolean isWindowValid() {
        if (windowStart == null || windowEnd == null) {
            return true;
        }
        return windowStart < windowEnd;
    }
}
