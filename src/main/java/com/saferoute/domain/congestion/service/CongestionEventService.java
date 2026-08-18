package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionSummaryItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionSummaryRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
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
    private final CongestionSummaryRepository congestionSummaryRepository;
    private final TrainingEventPublisher trainingEventPublisher;
    private final RouteRecalculationService routeRecalculationService;

    // Raspberry Pi / YOLO가 보고하는 혼잡 이벤트를 받아 DynamoDB에 저장하고, CROWDED이면 재탐색을 트리거한다.
    // 대상 건물에 RUNNING 세션이 없으면(훈련 중이 아니면) 저장/트리거 없이 조용히 종료한다.
    @Transactional
    public void reportCongestion(ReportCongestionRequest request) {
        MapEdge edge = mapEdgeJpaRepository.findById(request.edgeId())
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND));

        var buildingId = edge.getFloor().getBuilding().getId();
        TrainingSession session = trainingSessionRepository
                .findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId)
                .orElse(null);
        if (session == null) {
            log.debug("훈련 중이 아닌 건물의 혼잡 이벤트라 무시함: buildingId={}, edgeId={}", buildingId, edge.getId());
            return;
        }

        CongestionSummaryItem item = CongestionSummaryItem.create(
                session.getId().toString(),
                edge.getId().toString(),
                request.cctvCode(),
                request.avgHeadcount(),
                request.peakHeadcount(),
                request.congestionLevel(),
                request.windowStart(),
                request.windowEnd(),
                request.s3ImageKey()
        );
        congestionSummaryRepository.save(item);

        trainingEventPublisher.publishCongestionUpdated(session.getId(), edge.getId(), item);

        CongestionLevel level = request.congestionLevel();
        if (level == CongestionLevel.CROWDED) {
            routeRecalculationService.trigger(session, edge, level);
        }
    }
}
