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
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.List;
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

    private final RouteRecalculationRepository routeRecalculationRepository;
    private final EvacuationRouteService evacuationRouteService;
    private final IoTLightService ioTLightService;
    private final UserRepository userRepository;
    private final TrainingEventPublisher trainingEventPublisher;

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
        UUID startNodeId = triggerEdge.getFromNode().getId();

        RouteSnapshot previous = resolveActiveRoute(session, triggerEdge, floorId, startNodeId);

        EvacuationRoute candidate;
        try {
            candidate = evacuationRouteService.findShortestRoute(floorId, startNodeId, Set.of(triggerEdge.getId()));
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
    public List<RouteRecalculationSummaryResponse> getRecalculations(UUID trainingSessionId, RecalculationStatus status) {
        List<RouteRecalculation> recalculations = status != null
                ? routeRecalculationRepository.findAllByTrainingSession_IdAndStatusOrderByRequestedAtDesc(
                        trainingSessionId, status)
                : routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(trainingSessionId);
        return recalculations.stream().map(RouteRecalculationSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RouteRecalculationDetailResponse getRecalculationDetail(UUID recalculationId) {
        return RouteRecalculationDetailResponse.from(findOrThrow(recalculationId));
    }

    @Transactional
    public RouteRecalculationResponse approve(UUID recalculationId, String approverEmail) {
        RouteRecalculation recalculation = findOrThrow(recalculationId);
        validatePending(recalculation);
        User approver = findUserOrThrow(approverEmail);

        recalculation.approve(Instant.now(), approver);
        trainingEventPublisher.publishEvacuationRouteUpdatedAfterCommit(recalculation);
        ioTLightService.applyRouteGuidance(recalculation.getRecalculatedNodeIds());

        return RouteRecalculationResponse.from(recalculation);
    }

    @Transactional
    public RouteRecalculationResponse reject(UUID recalculationId, String rejecterEmail, String reason) {
        RouteRecalculation recalculation = findOrThrow(recalculationId);
        validatePending(recalculation);
        User rejecter = findUserOrThrow(rejecterEmail);

        recalculation.reject(Instant.now(), rejecter, reason);
        trainingEventPublisher.publishRouteRecalculationRejectedAfterCommit(recalculation);

        return RouteRecalculationResponse.from(recalculation);
    }

    private RouteRecalculation findOrThrow(UUID recalculationId) {
        return routeRecalculationRepository.findById(recalculationId)
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
