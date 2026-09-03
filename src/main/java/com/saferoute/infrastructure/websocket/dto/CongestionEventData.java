package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.List;
import java.util.UUID;

// 프론트가 카드 단위로 갱신 대상을 식별할 수 있도록 eventId/cctvCode/capturedAt/hasMonitoringImage를 포함한다 (이슈 #142).
// CCTV당 1 Observation = 1 이벤트 발행 원칙을 지키기 위해 영향받는 Edge 전체를 edgeIds 배열로 담는다 (이슈 #192).
// REST 응답(ObservationResponse)과 필드명을 맞춰 density를 함께 포함한다 (이슈 #192).
public record CongestionEventData(
        UUID eventId,
        List<UUID> edgeIds,
        String cctvCode,
        Double avgHeadcount,
        Integer peakHeadcount,
        Double density,
        CongestionLevel congestionLevel,
        Long windowStart,
        Long windowEnd,
        Long capturedAt,
        // 이미지는 presigned URL 만료 문제로 WebSocket payload에 직접 포함하지 않는다.
        // true면 프론트가 프레임 조회 REST API를 재호출해서 이미지를 받아야 한다 (이슈 #192).
        boolean hasMonitoringImage
) {

    public static CongestionEventData from(List<UUID> edgeIds, ObservationItem item) {
        return new CongestionEventData(
                UUID.fromString(item.getEventId()),
                edgeIds,
                item.getCctvCode(),
                item.getAvgHeadcount(),
                item.getPeakHeadcount(),
                item.getDensity(),
                item.getCongestionLevel(),
                item.getWindowStart(),
                item.getWindowEnd(),
                item.getCapturedAt(),
                item.getMonitoringImageKey() != null && !item.getMonitoringImageKey().isBlank()
        );
    }
}
