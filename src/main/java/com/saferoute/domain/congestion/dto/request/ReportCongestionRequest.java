package com.saferoute.domain.congestion.dto.request;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReportCongestionRequest(
        @NotNull UUID edgeId,
        String cctvCode,
        @NotNull @PositiveOrZero Integer avgHeadcount,
        @NotNull @PositiveOrZero Integer peakHeadcount,
        @NotNull CongestionLevel congestionLevel,
        @NotNull Long windowStart,
        @NotNull Long windowEnd,
        String s3ImageKey
) {
    @AssertTrue(message = "avgHeadcount must not exceed peakHeadcount")
    public boolean isHeadcountValid() {
        if (avgHeadcount == null || peakHeadcount == null) {
            return true;
        }
        return avgHeadcount <= peakHeadcount;
    }

    @AssertTrue(message = "windowStart must be before windowEnd")
    public boolean isWindowValid() {
        if (windowStart == null || windowEnd == null) {
            return true;
        }
        return windowStart < windowEnd;
    }
}
