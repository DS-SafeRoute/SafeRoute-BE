package com.saferoute.domain.evacuation.recalculation.dto.response;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RouteRecalculationResponse(
        UUID id,
        UUID trainingSessionId,
        UUID triggerEdgeId,
        CongestionLevel congestionLevel,
        List<UUID> recalculatedNodeIds,
        double totalWeight,
        RecalculationStatus status,
        Instant requestedAt,
        Instant resolvedAt
) {

    public static RouteRecalculationResponse from(RouteRecalculation recalculation) {
        return new RouteRecalculationResponse(
                recalculation.getId(),
                recalculation.getTrainingSession().getId(),
                recalculation.getTriggerEdge().getId(),
                recalculation.getCongestionLevel(),
                recalculation.getRecalculatedNodeIds(),
                recalculation.getTotalWeight(),
                recalculation.getStatus(),
                recalculation.getRequestedAt(),
                recalculation.getResolvedAt()
        );
    }
}
