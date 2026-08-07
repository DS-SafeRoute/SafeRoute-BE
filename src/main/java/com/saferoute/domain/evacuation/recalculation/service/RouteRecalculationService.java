package com.saferoute.domain.evacuation.recalculation.service;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.telemetry.dynamo.entity.EventType;
import com.saferoute.domain.telemetry.dynamo.entity.TrainingEventItem;
import com.saferoute.domain.telemetry.dynamo.repository.TrainingEventRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteRecalculationService {

    private static final String TRIGGER_TYPE_CONGESTION = "CONGESTION";

    private final RouteRecalculationRepository routeRecalculationRepository;
    private final EvacuationRouteService evacuationRouteService;
    private final TrainingEventRepository trainingEventRepository;
    private final TrainingEventPublisher trainingEventPublisher;

    // 혼잡 감지로 트리거되는 우회 경로 재탐색. 같은 세션+엣지에 이미 PENDING이 있으면 새로 만들지 않고,
    // 우회 경로 자체가 없으면 로그만 남기고 승인 대기 항목을 만들지 않는다 (관리자 "재탐색 실패" 알림은 범위 밖).
    @Transactional
    public void trigger(TrainingSession session, MapEdge triggerEdge, CongestionLevel level) {
        if (routeRecalculationRepository.existsByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                session.getId(), triggerEdge.getId(), RecalculationStatus.PENDING)) {
            return;
        }

        UUID floorId = triggerEdge.getFloor().getId();
        // TODO: 양방향 엣지에서 실제로는 반대편 기준 우회도 필요할 수 있음 - 일단 fromNode 기준으로 고정
        UUID startNodeId = triggerEdge.getFromNode().getId();

        EvacuationRoute route;
        try {
            route = evacuationRouteService.findShortestRoute(floorId, startNodeId, Set.of(triggerEdge.getId()));
        } catch (ApiException exception) {
            if (exception.getErrorCode() == EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND) {
                log.warn("우회 경로를 찾을 수 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}, edgeId={}",
                        session.getId(), triggerEdge.getId());
                return;
            }
            throw exception;
        }

        List<UUID> newPathNodeIds = route.path().stream().map(node -> node.getId()).toList();

        RouteRecalculation recalculation = routeRecalculationRepository.save(
                RouteRecalculation.createPending(session, triggerEdge, level, newPathNodeIds, route.totalWeight())
        );

        trainingEventRepository.save(TrainingEventItem.create(
                session.getId().toString(),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli(),
                EventType.ROUTE_RECALCULATED,
                TRIGGER_TYPE_CONGESTION,
                List.of(triggerEdge.getId().toString()),
                null,
                newPathNodeIds.stream().map(UUID::toString).toList(),
                null,
                null,
                null
        ));

        trainingEventPublisher.publishRouteRecalculationRequestedAfterCommit(recalculation);
    }
}
