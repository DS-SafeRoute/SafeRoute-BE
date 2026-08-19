package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.IdempotentSaveResult;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionEventService {

    private final MapEdgeJpaRepository mapEdgeJpaRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final ObservationRepository observationRepository;
    private final TrainingEventPublisher trainingEventPublisher;
    private final RouteRecalculationService routeRecalculationService;

    // Raspberry Pi / YOLO가 보고하는 혼잡 이벤트를 받아 DynamoDB에 저장하고, CROWDED이면 재탐색을 트리거한다.
    // 대상 건물에 RUNNING 세션이 없으면 공통 에러 응답으로 처리한다.
    @Transactional
    public IdempotentSaveResult<ObservationItem> reportCongestion(ReportCongestionRequest request) {
        MapEdge edge = mapEdgeJpaRepository.findById(request.edgeId())
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND));

        var buildingId = edge.getFloor().getBuilding().getId();
        TrainingSession session = trainingSessionRepository
                .findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        ObservationItem item = ObservationItem.create(
                request.eventId(),
                session.getId(),
                request.cctvCode(),
                request.avgHeadcount(),
                request.peakHeadcount(),
                request.sampleCount(),
                request.density(),
                request.congestionLevel(),
                request.windowStart(),
                request.windowEnd(),
                request.capturedAt(),
                request.monitoringImageKey(),
                request.configVersion()
        );
        IdempotentSaveResult<ObservationItem> saveResult = observationRepository.saveIfAbsent(item);
        if (!saveResult.created()) {
            return saveResult;
        }

        trainingEventPublisher.publishCongestionUpdated(session.getId(), edge.getId(), saveResult.item());

        CongestionLevel level = request.congestionLevel();
        if (level.requiresRouteRecalculation()) {
            routeRecalculationService.trigger(session, edge, level);
        }
        return saveResult;
    }
}
