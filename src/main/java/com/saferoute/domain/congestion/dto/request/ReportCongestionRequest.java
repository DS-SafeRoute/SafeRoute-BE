package com.saferoute.domain.congestion.dto.request;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReportCongestionRequest(
        @NotNull UUID edgeId,
        String cctvCode,
        Integer avgHeadcount,
        Integer peakHeadcount,
        @NotNull CongestionLevel congestionLevel,
        @NotNull Long windowStart,
        @NotNull Long windowEnd,
        String s3ImageKey
) {}
