package com.saferoute.domain.evacuation.recalculation.dto.response;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.time.Instant;
import java.util.UUID;

// 승인 대기 목록 화면용 - 경로 상세는 포함하지 않는다 (상세 조회 API 참고).
public record RouteRecalculationSummaryResponse(
        UUID recalculationId,
        UUID trainingSessionId,
        String cctvCode,
        UUID triggerEdgeId,
        RecalculationTriggerType triggerType,
        CongestionLevel congestionLevel,
        double density,
        RecalculationStatus status,
        Instant requestedAt
) {

    public static RouteRecalculationSummaryResponse from(RouteRecalculation recalculation) {
        return new RouteRecalculationSummaryResponse(
                recalculation.getId(),
                recalculation.getTrainingSession().getId(),
                recalculation.getCctvCode(),
                recalculation.getTriggerEdge().getId(),
                recalculation.getTriggerType(),
                recalculation.getCongestionLevel(),
                recalculation.getDensity(),
                recalculation.getStatus(),
                recalculation.getRequestedAt()
        );
    }
}
