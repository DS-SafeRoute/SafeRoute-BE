package com.saferoute.domain.training.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "이벤트 타임라인 항목")
public record MonitoringEventResponse(
        @Schema(
                description = "이벤트 ID. 혼잡 이벤트는 CongestionEventItem의 eventId, 경로 재탐색은 "
                        + "recalculationId에 상태 접미사를 붙인 값(같은 재탐색이 요청/해소 두 항목으로 나뉠 수 있어서)",
                example = "3c9f7e2a-3b39-4f0a-9f0a-6a2b6b1f5a11"
        )
        String eventId,

        @Schema(description = "이벤트 종류")
        MonitoringEventType type,

        @Schema(description = "심각도")
        MonitoringEventSeverity severity,

        @Schema(description = "발생 시각(Unix epoch milliseconds)", example = "1787722095000")
        long occurredAt,

        @Schema(description = "관련 CCTV 코드", example = "CCTV_001")
        String cctvCode,

        @Schema(description = "이벤트 시점의 혼잡 단계", example = "CROWDED", nullable = true)
        CongestionLevel congestionLevel,

        @Schema(description = "사용자 표시 문구", example = "혼잡 감지 · CCTV_001")
        String message
) {

    public static MonitoringEventResponse fromCongestionEvent(CongestionEventItem item) {
        MonitoringEventType type = MonitoringEventType.from(item.getEventType());
        return new MonitoringEventResponse(
                item.getEventId(),
                type,
                type.severity(item.getCongestionLevel()),
                item.getDetectedAt(),
                item.getCctvCode(),
                item.getCongestionLevel(),
                type.message(item.getCctvCode(), item.getCongestionLevel())
        );
    }

    public static MonitoringEventResponse requestedFrom(RouteRecalculation recalculation) {
        MonitoringEventType type = MonitoringEventType.ROUTE_RECALCULATION_REQUESTED;
        return new MonitoringEventResponse(
                recalculation.getId() + ":REQUESTED",
                type,
                type.severity(recalculation.getCongestionLevel()),
                recalculation.getRequestedAt().toEpochMilli(),
                recalculation.getCctvCode(),
                recalculation.getCongestionLevel(),
                type.message(recalculation.getCctvCode(), recalculation.getCongestionLevel())
        );
    }

    // recalculation.getResolvedAt()이 null이 아닌 경우에만 호출해야 한다 (MonitoringEventType.fromResolution 참고).
    public static MonitoringEventResponse resolvedFrom(RouteRecalculation recalculation) {
        MonitoringEventType type = MonitoringEventType.fromResolution(recalculation.getStatus());
        return new MonitoringEventResponse(
                recalculation.getId() + ":" + recalculation.getStatus(),
                type,
                type.severity(recalculation.getCongestionLevel()),
                recalculation.getResolvedAt().toEpochMilli(),
                recalculation.getCctvCode(),
                recalculation.getCongestionLevel(),
                type.message(recalculation.getCctvCode(), recalculation.getCongestionLevel())
        );
    }
}
