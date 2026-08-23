package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import java.util.List;
import java.util.UUID;

public record RouteRecalculationEventData(
        UUID recalculationId,
        UUID triggerEdgeId,
        CongestionLevel congestionLevel,
        List<UUID> recalculatedNodeIds,
        double totalWeight,
        RecalculationStatus status,
        String reason
) {

    public static RouteRecalculationEventData from(RouteRecalculation recalculation) {
        String reason = recalculation.getStatus() == RecalculationStatus.REJECTED
                ? recalculation.getRejectReason()
                : recalculation.getCancelReason();
        return new RouteRecalculationEventData(
                recalculation.getId(),
                recalculation.getTriggerEdge().getId(),
                recalculation.getCongestionLevel(),
                recalculation.getRecalculatedNodeIds(),
                recalculation.getTotalWeight(),
                recalculation.getStatus(),
                reason
        );
    }
}
