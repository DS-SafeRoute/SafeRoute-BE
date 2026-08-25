package com.saferoute.domain.evacuation.recalculation.service;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationDetailResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationSummaryResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    // CAUTION은 1.5배, CROWDED는 3배 페널티만 주고 여전히 후보에 남긴다.
    // VERY_CROWDED는 배율이 아니라 완전 제외(excludedEdgeIds)로 처리한다 - trigger()의
    // requiresRouteRecalculation() 게이트 상 CAUTION은 현재 이 메서드까지 도달하지 않지만,
    // 표 전체를 그대로 반영해둔다.
    private static final double CAUTION_WEIGHT_MULTIPLIER = 1.5;
    private static final double CROWDED_WEIGHT_MULTIPLIER = 3.0;

    private final RouteRecalculationRepository routeRecalculationRepository;
    private final EvacuationRouteService evacuationRouteService;
    private final IoTLightService ioTLightService;
    private final UserRepository userRepository;
    private final TrainingEventPublisher trainingEventPublisher;
    private final SchoolContextService schoolContextService;

    // 혼잡 감지로 트리거되는 우회 경로 재탐색.
    // - 같은 세션+엣지에 이미 PENDING이 있고 레벨이 그대로면 반복 트리거를 무시한다.
    // - 레벨이 바뀌었으면 기존 PENDING을 CANCELLED로 무효화하고 새로 계산한다.
    // - triggerType이 ENDED(혼잡 종료)면 우회가 아니라 정상 경로로의 복구 후보를 계산한다.
    @Transactional
    public void trigger(TrainingSession session, MapEdge triggerEdge, CongestionLevel level,
            RecalculationTriggerType triggerType, String cctvCode, double density) {
        Optional<RouteRecalculation> existingPending = routeRecalculationRepository
                .findByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                        session.getId(), triggerEdge.getId(), RecalculationStatus.PENDING);

        if (triggerType == RecalculationTriggerType.ENDED) {
            existingPending.ifPresent(pending -> cancel(pending, "혼잡 종료로 무효화됨"));
            triggerRecovery(session, triggerEdge, level, cctvCode, density);
            return;
        }

        if (existingPending.isPresent()) {
            RouteRecalculation pending = existingPending.get();
            if (pending.getCongestionLevel() == level) {
                return;
            }
            cancel(pending, "혼잡 단계 변경으로 무효화됨 (" + pending.getCongestionLevel() + " -> " + level + ")");
        }

        UUID floorId = triggerEdge.getFloor().getId();
        // TODO: 양방향 엣지에서는 실제로 반대편(toNode) 기준 우회도 필요할 수 있다 - 지금은
        // fromNode 기준으로 고정한다
        UUID startNodeId = triggerEdge.getFromNode().getId();

        RouteSnapshot previous = resolveActiveRoute(session, triggerEdge, floorId, startNodeId);

        EvacuationRoute candidate;
        try {
            candidate = evacuationRouteService.findShortestRoute(
                    floorId, startNodeId, excludedEdgesFor(triggerEdge, level), weightMultipliersFor(triggerEdge, level));
        } catch (ApiException exception) {
            if (exception.getErrorCode() == EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND) {
                log.warn("우회 경로를 찾을 수 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}, edgeId={}",
                        session.getId(), triggerEdge.getId());
                return;
            }
            throw exception;
        }

        savePending(session, triggerEdge, cctvCode, triggerType, level, density, previous, candidate);
    }

    // VERY_CROWDED만 완전 제외한다 - 배율만으로는 다른 대안이 훨씬 나쁠 때 여전히 그 엣지를
    // 통과하는 경로가 선택될 수 있어, "사실상 통행 불가"를 표현하려면 그래프에서 아예 빼야 한다.
    private Set<UUID> excludedEdgesFor(MapEdge triggerEdge, CongestionLevel level) {
        return level == CongestionLevel.VERY_CROWDED ? Set.of(triggerEdge.getId()) : Set.of();
    }

    private Map<UUID, Double> weightMultipliersFor(MapEdge triggerEdge, CongestionLevel level) {
        return switch (level) {
            case CAUTION -> Map.of(triggerEdge.getId(), CAUTION_WEIGHT_MULTIPLIER);
            case CROWDED -> Map.of(triggerEdge.getId(), CROWDED_WEIGHT_MULTIPLIER);
            case NORMAL, VERY_CROWDED -> Map.of();
        };
    }

    // 혼잡 종료 시 정상(트리거 엣지를 포함한 직행) 경로로의 복구 후보를 계산한다.
    // 현재 활성 경로가 이미 그 엣지를 포함한 직행 경로라면(=승인된 우회가 없다면) 복구할 게 없으므로 아무것도 하지 않는다.
    private void triggerRecovery(TrainingSession session, MapEdge triggerEdge, CongestionLevel level,
            String cctvCode, double density) {
        Optional<RouteRecalculation> latestApproved = routeRecalculationRepository
                .findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
                        session.getId(), triggerEdge.getId(), RecalculationStatus.APPROVED);
        if (latestApproved.isEmpty()) {
            return;
        }
        RouteRecalculation activeDetour = latestApproved.get();

        UUID floorId = triggerEdge.getFloor().getId();
        UUID startNodeId = triggerEdge.getFromNode().getId();

        EvacuationRoute recovery;
        try {
            recovery = evacuationRouteService.findShortestRoute(floorId, startNodeId);
        } catch (ApiException exception) {
            if (exception.getErrorCode() == EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND) {
                log.warn("복구 경로를 찾을 수 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}, edgeId={}",
                        session.getId(), triggerEdge.getId());
                return;
            }
            throw exception;
        }

        List<UUID> recoveryNodeIds = recovery.path().stream().map(node -> node.getId()).toList();
        if (recoveryNodeIds.equals(activeDetour.getRecalculatedNodeIds())) {
            // 복구 후보가 현재 활성 경로와 동일 - 새로운 승인 요청을 만들 필요가 없다.
            return;
        }

        RouteSnapshot previous = new RouteSnapshot(activeDetour.getRecalculatedNodeIds(), activeDetour.getTotalWeight());
        RouteRecalculation recalculation = save(RouteRecalculation.createPending(
                session, triggerEdge, cctvCode, RecalculationTriggerType.ENDED, level, density,
                previous.nodeIds(), previous.totalWeight(), recoveryNodeIds, recovery.totalWeight()));
        trainingEventPublisher.publishRouteRecalculationRequestedAfterCommit(recalculation);
    }

    // "현재 활성 경로"를 별도로 저장하지 않으므로, 가장 최근 승인된 경로가 있으면 그것을,
    // 없으면 트리거 엣지를 그대로 포함한 정상(직행) 경로를 활성 경로로 취급한다.
    private RouteSnapshot resolveActiveRoute(TrainingSession session, MapEdge triggerEdge, UUID floorId, UUID startNodeId) {
        Optional<RouteRecalculation> latestApproved = routeRecalculationRepository
                .findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
                        session.getId(), triggerEdge.getId(), RecalculationStatus.APPROVED);
        if (latestApproved.isPresent()) {
            RouteRecalculation approved = latestApproved.get();
            return new RouteSnapshot(approved.getRecalculatedNodeIds(), approved.getTotalWeight());
        }

        try {
            EvacuationRoute direct = evacuationRouteService.findShortestRoute(floorId, startNodeId);
            List<UUID> nodeIds = direct.path().stream().map(node -> node.getId()).toList();
            return new RouteSnapshot(nodeIds, direct.totalWeight());
        } catch (ApiException exception) {
            // 정상 경로조차 없으면(EXIT 미지정 등) 비교 기준 없이 후보만 제시한다.
            return new RouteSnapshot(List.of(), 0.0);
        }
    }

    private void savePending(TrainingSession session, MapEdge triggerEdge, String cctvCode,
            RecalculationTriggerType triggerType, CongestionLevel level, double density,
            RouteSnapshot previous, EvacuationRoute candidate) {
        List<UUID> candidateNodeIds = candidate.path().stream().map(node -> node.getId()).toList();
        RouteRecalculation recalculation = save(RouteRecalculation.createPending(
                session, triggerEdge, cctvCode, triggerType, level, density,
                previous.nodeIds(), previous.totalWeight(), candidateNodeIds, candidate.totalWeight()));
        trainingEventPublisher.publishRouteRecalculationRequestedAfterCommit(recalculation);
    }

    private RouteRecalculation save(RouteRecalculation recalculation) {
        return routeRecalculationRepository.save(recalculation);
    }

    private void cancel(RouteRecalculation recalculation, String reason) {
        recalculation.cancel(Instant.now(), reason);
        trainingEventPublisher.publishRouteRecalculationCancelledAfterCommit(recalculation);
    }

    // 훈련 세션 종료(정상/강제/타임아웃) 시 그 세션의 남은 PENDING을 전부 무효화한다.
    @Transactional
    public void cancelAllPendingForSession(UUID sessionId, String reason) {
        List<RouteRecalculation> pendingList = routeRecalculationRepository
                .findAllByTrainingSession_IdAndStatus(sessionId, RecalculationStatus.PENDING);
        for (RouteRecalculation pending : pendingList) {
            cancel(pending, reason);
        }
    }

    @Transactional(readOnly = true)
    public List<RouteRecalculationSummaryResponse> getRecalculations(
            UUID trainingSessionId, RecalculationStatus status, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        List<RouteRecalculation> recalculations = status != null
                ? routeRecalculationRepository
                        .findAllByTrainingSession_IdAndStatusAndTrainingSession_Scenario_Building_SchoolNameOrderByRequestedAtDesc(
                                trainingSessionId, status, schoolName)
                : routeRecalculationRepository
                        .findAllByTrainingSession_IdAndTrainingSession_Scenario_Building_SchoolNameOrderByRequestedAtDesc(
                                trainingSessionId, schoolName);
        return recalculations.stream().map(RouteRecalculationSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RouteRecalculationDetailResponse getRecalculationDetail(UUID recalculationId, String email) {
        return RouteRecalculationDetailResponse.from(findOrThrow(recalculationId, email));
    }

    @Transactional
    public RouteRecalculationResponse approve(UUID recalculationId, String approverEmail) {
        RouteRecalculation recalculation = findOrThrow(recalculationId, approverEmail);
        validatePending(recalculation);
        User approver = findUserOrThrow(approverEmail);

        recalculation.approve(Instant.now(), approver);
        trainingEventPublisher.publishEvacuationRouteUpdatedAfterCommit(recalculation);
        ioTLightService.applyRouteGuidance(recalculation.getRecalculatedNodeIds());

        return RouteRecalculationResponse.from(recalculation);
    }

    @Transactional
    public RouteRecalculationResponse reject(UUID recalculationId, String rejecterEmail, String reason) {
        RouteRecalculation recalculation = findOrThrow(recalculationId, rejecterEmail);
        validatePending(recalculation);
        User rejecter = findUserOrThrow(rejecterEmail);

        recalculation.reject(Instant.now(), rejecter, reason);
        trainingEventPublisher.publishRouteRecalculationRejectedAfterCommit(recalculation);

        return RouteRecalculationResponse.from(recalculation);
    }

    private RouteRecalculation findOrThrow(UUID recalculationId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        return routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(recalculationId, schoolName)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND));
    }

    private User findUserOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.ADMIN_NOT_FOUND));
    }

    // 상태 전이 검증은 엔티티가 아니라 여기서 한다
    // (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션).
    private void validatePending(RouteRecalculation recalculation) {
        if (recalculation.getStatus() != RecalculationStatus.PENDING) {
            throw new ApiException(EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION);
        }
    }

    private record RouteSnapshot(List<UUID> nodeIds, double totalWeight) {
    }
}
