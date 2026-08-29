package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.UUID;

// 프론트가 카드 단위로 갱신 대상을 식별할 수 있도록 eventId/cctvCode/capturedAt/hasMonitoringImage를 포함한다 (이슈 #142).
public record CongestionEventData(
        UUID eventId,
        UUID edgeId,
        String cctvCode,
        Double avgHeadcount,
        Integer peakHeadcount,
        CongestionLevel congestionLevel,
        Long windowStart,
        Long windowEnd,
        Long capturedAt,
        boolean hasMonitoringImage
) {

    public static CongestionEventData from(UUID edgeId, ObservationItem item) {
        return new CongestionEventData(
                UUID.fromString(item.getEventId()),
                edgeId,
                item.getCctvCode(),
                item.getAvgHeadcount(),
                item.getPeakHeadcount(),
                item.getCongestionLevel(),
                item.getWindowStart(),
                item.getWindowEnd(),
                item.getCapturedAt(),
                item.getMonitoringImageKey() != null && !item.getMonitoringImageKey().isBlank()
        );
    }
}
