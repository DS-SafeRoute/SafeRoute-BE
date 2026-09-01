package com.saferoute.domain.evacuation.recalculation.dto.response;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.entity.DensityUnit;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.floor.entity.Floor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 관리자가 기존 경로(previousRoute)와 새 후보(candidateRoute)를 비교할 수 있도록 제공하는 상세 조회 응답.
public record RouteRecalculationDetailResponse(
        UUID recalculationId,
        UUID trainingSessionId,
        UUID floorId,
        Integer floorNum,
        String locationName,
        String cctvCode,
        UUID triggerEdgeId,
        RecalculationTriggerType triggerType,
        CongestionLevel congestionLevel,
        double density,
        DensityUnit densityUnit,
        RouteSegment previousRoute,
        RouteSegment candidateRoute,
        RecalculationStatus status,
        Instant requestedAt,
        Instant resolvedAt,
        String resolvedBy,
        String rejectReason,
        String cancelReason
) {

    public record RouteSegment(List<UUID> nodeIds, double totalWeight) {
    }

    public static RouteRecalculationDetailResponse from(RouteRecalculation recalculation) {
        Floor floor = recalculation.getTriggerEdge().getFloor();
        return new RouteRecalculationDetailResponse(
                recalculation.getId(),
                recalculation.getTrainingSession().getId(),
                floor.getId(),
                floor.getFloorNum(),
                floor.getFloorNum() + "층",
                recalculation.getCctvCode(),
                recalculation.getTriggerEdge().getId(),
                recalculation.getTriggerType(),
                recalculation.getCongestionLevel(),
                recalculation.getDensity(),
                DensityUnit.PERSON_PER_SQUARE_METER,
                // @ElementCollection(LAZY) - 트랜잭션이 살아있는 지금 List.copyOf로 즉시 초기화한다
                // (open-in-view: false 환경에서 직렬화 시점의 LazyInitializationException 방지, RouteRecalculationResponse 참고).
                new RouteSegment(List.copyOf(recalculation.getPreviousNodeIds()), recalculation.getPreviousTotalWeight()),
                new RouteSegment(List.copyOf(recalculation.getRecalculatedNodeIds()), recalculation.getTotalWeight()),
                recalculation.getStatus(),
                recalculation.getRequestedAt(),
                recalculation.getResolvedAt(),
                recalculation.getResolvedBy() != null ? recalculation.getResolvedBy().getUsername() : null,
                recalculation.getRejectReason(),
                recalculation.getCancelReason()
        );
    }
}
