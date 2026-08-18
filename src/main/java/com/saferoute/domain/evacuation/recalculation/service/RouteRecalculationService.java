package com.saferoute.domain.evacuation.recalculation.service;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteRecalculationService {

    private final RouteRecalculationRepository routeRecalculationRepository;
    private final EvacuationRouteService evacuationRouteService;
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

        RouteRecalculation recalculation;
        try {
            recalculation = routeRecalculationRepository.save(
                    RouteRecalculation.createPending(session, triggerEdge, level, newPathNodeIds, route.totalWeight())
            );
        } catch (DataIntegrityViolationException exception) {
            // 동시에 들어온 중복 혼잡 이벤트가 exists() 체크를 함께 통과한 경우, DB 유니크 제약으로 걸러진 것이므로 조용히 무시한다.
            log.debug("동시 요청으로 인한 중복 재탐색 저장을 무시함: sessionId={}, edgeId={}", session.getId(), triggerEdge.getId());
            return;
        }

        trainingEventPublisher.publishRouteRecalculationRequestedAfterCommit(recalculation);
    }

    @Transactional
    public RouteRecalculationResponse approve(UUID recalculationId) {
        RouteRecalculation recalculation = findOrThrow(recalculationId);
        validatePending(recalculation);

        recalculation.approve(Instant.now());
        trainingEventPublisher.publishEvacuationRouteUpdatedAfterCommit(recalculation);

        return RouteRecalculationResponse.from(recalculation);
    }

    @Transactional
    public RouteRecalculationResponse reject(UUID recalculationId) {
        RouteRecalculation recalculation = findOrThrow(recalculationId);
        validatePending(recalculation);

        recalculation.reject(Instant.now());

        return RouteRecalculationResponse.from(recalculation);
    }

    private RouteRecalculation findOrThrow(UUID recalculationId) {
        return routeRecalculationRepository.findById(recalculationId)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND));
    }

    // 상태 전이 검증은 엔티티가 아니라 여기서 한다
    // (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션).
    private void validatePending(RouteRecalculation recalculation) {
        if (recalculation.getStatus() != RecalculationStatus.PENDING) {
            throw new ApiException(EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION);
        }
    }
}
