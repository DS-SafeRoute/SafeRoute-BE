package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionSummaryItem;
import java.util.UUID;

public record CongestionEventData(
        UUID edgeId,
        Integer avgHeadcount,
        Integer peakHeadcount,
        CongestionLevel congestionLevel,
        Long windowStart,
        Long windowEnd
) {

    public static CongestionEventData from(UUID edgeId, CongestionSummaryItem item) {
        return new CongestionEventData(
                edgeId,
                item.getAvgHeadcount(),
                item.getPeakHeadcount(),
                item.getCongestionLevel(),
                item.getWindowStart(),
                item.getWindowEnd()
        );
    }
}
