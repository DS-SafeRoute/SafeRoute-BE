package com.saferoute.domain.evacuation.recalculation.service;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.dto.response.CurrentRouteResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationDetailResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationSummaryResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final TrainingSessionRepository trainingSessionRepository;
    private final MapNodeJpaRepository mapNodeJpaRepository;
    private final FloorGridCellRepository floorGridCellRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;

    // 혼잡 감지로 트리거되는 우회 경로 재탐색.
    // - CCTV 한 대가 감시하는 모든 엣지를 한 번에 반영해 후보 경로 하나만 만든다.
    // - 같은 세션+CCTV에 이미 PENDING이 있고 레벨이 그대로면 반복 트리거를 무시한다.
    // - 새 판단이 필요하면 기존 PENDING을 모두 CANCELLED로 무효화하고 새로 계산한다.
    // - triggerType이 ENDED(혼잡 종료)면 우회가 아니라 정상 경로로의 복구 후보를 계산한다.
    @Transactional
    public void trigger(TrainingSession session, List<MapEdge> affectedEdges, CongestionLevel level,
            RecalculationTriggerType triggerType, String cctvCode, double density) {
        if (affectedEdges.isEmpty()) {
            return;
        }

        TrainingSession lockedSession = trainingSessionRepository.findByIdForUpdate(session.getId()).orElse(session);
        MapEdge representativeEdge = affectedEdges.get(0);
        List<RouteRecalculation> existingPending = routeRecalculationRepository
                .findAllByTrainingSession_IdAndStatus(session.getId(), RecalculationStatus.PENDING);

        if (triggerType == RecalculationTriggerType.ENDED) {
            existingPending.forEach(pending -> cancel(pending, "혼잡 종료로 무효화됨"));
            triggerRecovery(lockedSession, representativeEdge, level, cctvCode, density);
            return;
        }

        // getCctvCode()는 FIRE_SPREAD 트리거 건에서는 null이라(RouteRecalculation 참고),
        // 같은 세션에 화재 유발 PENDING이 섞여 있어도 NPE 없이 비교되도록 null-safe하게 비교한다.
        boolean samePendingExists = existingPending.stream().anyMatch(pending ->
                Objects.equals(pending.getCctvCode(), cctvCode) && pending.getCongestionLevel() == level);
        if (samePendingExists) {
            return;
        }
        for (RouteRecalculation pending : existingPending) {
            cancel(pending, "새 혼잡 판단으로 무효화됨");
        }

        UUID floorId = representativeEdge.getFloor().getId();
        // 현재 유효 경로는 항상 "시나리오 대표 startNode -> EXIT" 완전한 한 경로여야 하므로,
        // 혼잡 엣지의 fromNode가 아니라 시나리오의 대표 startNode에서 다시 계산한다.
        MapNode representativeStart = lockedSession.getScenario().getStartNode();
        if (representativeStart == null) {
            log.warn("시나리오에 대표 startNode가 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}",
                    lockedSession.getId());
            return;
        }
        UUID startNodeId = representativeStart.getId();

        RouteSnapshot previous = resolveActiveRoute(lockedSession, floorId, startNodeId);

        EvacuationRoute candidate;
        try {
            candidate = evacuationRouteService.findShortestRoute(
                    floorId, startNodeId, excludedEdgesFor(affectedEdges, level),
                    weightMultipliersFor(affectedEdges, level));
        } catch (ApiException exception) {
            if (exception.getErrorCode() == EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND) {
                log.warn("우회 경로를 찾을 수 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}, edgeId={}",
                        lockedSession.getId(), representativeEdge.getId());
                return;
            }
            throw exception;
        }

        List<UUID> candidateNodeIds = candidate.path().stream().map(MapNode::getId).toList();
        if (candidateNodeIds.equals(previous.nodeIds())) {
            // 혼잡이 지속되는 동안 관측값마다 반복 트리거돼도(CongestionObservationService 참고),
            // 이미 반영된 경로와 후보가 같으면 동일한 PENDING을 계속 새로 만들 필요가 없다.
            return;
        }

        savePending(lockedSession, representativeEdge, cctvCode, triggerType, level, density,
                previous, candidate, candidateNodeIds);
    }

    // 화재 확산으로 트리거되는 우회 경로 재탐색.
    // - affectedEdges는 "현재 그 층에서 불이 붙은 모든 구간"이어야 한다(이번 틱에 새로 옮겨붙은
    //   구간만이 아니라 누적본). 화재는 꺼지지 않으므로 매번 전체 화재 구간을 다시 완전 제외해야
    //   이전 스텝에서 제외했던 구간이 다시 후보 경로에 섞여 들어가는 일이 없다.
    // - 혼잡과 달리 화재는 가중치가 아니라 항상 완전 제외(hard exclusion)로 처리한다.
    // - CongestionLevel/cctvCode/density 개념이 없으므로 별도 팩토리(createPendingForFireSpread)로 저장한다.
    // - 기존 PENDING 중 이번에 새로 화재 구간이 된 엣지를 실제로 지나가는 건은, 새 대안 유무와
    //   무관하게 즉시 무효화한다(관리자가 나중에 화재 통과 경로를 실수로 승인하는 일을 막기 위함).
    //   화재와 무관한 나머지 PENDING은 "새 후보를 실제로 저장하기 직전"에만 무효화한다 - 우회
    //   경로를 못 찾거나 후보가 활성 경로와 같아 새로 저장할 게 없다면 그대로 둔다.
    @Transactional
    public void triggerForFireSpread(TrainingSession session, List<MapEdge> affectedEdges) {
        if (affectedEdges.isEmpty()) {
            return;
        }

        TrainingSession lockedSession = trainingSessionRepository.findByIdForUpdate(session.getId()).orElse(session);

        List<RouteRecalculation> existingPending = routeRecalculationRepository
                .findAllByTrainingSession_IdAndStatus(session.getId(), RecalculationStatus.PENDING);
        List<RouteRecalculation> stillSafePending = new ArrayList<>();
        for (RouteRecalculation pending : existingPending) {
            if (crossesAnyEdge(pending.getRecalculatedNodeIds(), affectedEdges)) {
                cancel(pending, "화재 확산으로 무효화됨");
            } else {
                stillSafePending.add(pending);
            }
        }

        UUID floorId = affectedEdges.get(0).getFloor().getId();
        MapNode representativeStart = lockedSession.getScenario().getStartNode();
        if (representativeStart == null) {
            log.warn("시나리오에 대표 startNode가 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}",
                    lockedSession.getId());
            return;
        }
        UUID startNodeId = representativeStart.getId();

        RouteSnapshot previous = resolveActiveRoute(lockedSession, floorId, startNodeId);

        // 지금 안내 중인 활성 경로가 이번에 새로 화재 구간이 된 엣지와 무관하면 다시 계산할 필요가
        // 없다. 여기서 걸러내지 않으면, 화재와 무관한 곳에서 불이 나도(가중치/제외 없이 화재만
        // 피한) 후보가 현재 활성 경로(예: 이미 승인된 혼잡 우회)와 달라져 버려서, 여전히 유효한
        // 혼잡 우회를 원래의(다시 혼잡한) 직행 경로로 되돌리는 PENDING이 매 틱 반복 생성된다.
        MapEdge representativeEdge = findCrossedEdge(previous.nodeIds(), affectedEdges).orElse(null);
        if (representativeEdge == null) {
            return;
        }

        Set<UUID> excludedEdgeIds = affectedEdges.stream().map(MapEdge::getId).collect(Collectors.toSet());
        EvacuationRoute candidate;
        try {
            candidate = evacuationRouteService.findShortestRoute(floorId, startNodeId, excludedEdgeIds, Map.of());
        } catch (ApiException exception) {
            if (exception.getErrorCode() == EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND) {
                log.warn("화재로 막힌 구간을 피해 우회 경로를 찾을 수 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}",
                        lockedSession.getId());
                return;
            }
            throw exception;
        }

        List<UUID> candidateNodeIds = candidate.path().stream().map(MapNode::getId).toList();
        if (candidateNodeIds.equals(previous.nodeIds())) {
            return;
        }

        for (RouteRecalculation pending : stillSafePending) {
            cancel(pending, "화재 확산으로 무효화됨");
        }

        RouteRecalculation recalculation = save(RouteRecalculation.createPendingForFireSpread(
                lockedSession, representativeEdge, previous.nodeIds(), previous.totalWeight(),
                candidateNodeIds, candidate.totalWeight()));
        trainingEventPublisher.publishRouteRecalculationRequestedAfterCommit(recalculation);
    }

    // nodeIds(순서대로 이어진 경로)가 edges 중 하나라도 연속된 두 노드로 포함하면 그 구간을
    // 지나간다고 본다. 엣지 저장 방향과 실제 이동 방향이 다를 수 있어(양방향 통행) 순서 상관없이
    // 두 노드 쌍이 일치하는지만 확인한다.
    private boolean crossesAnyEdge(List<UUID> nodeIds, List<MapEdge> edges) {
        return findCrossedEdge(nodeIds, edges).isPresent();
    }

    // crossesAnyEdge와 같은 기준으로, 실제로 경로가 지나가는 첫 번째 엣지를 돌려준다.
    private Optional<MapEdge> findCrossedEdge(List<UUID> nodeIds, List<MapEdge> edges) {
        if (nodeIds.size() < 2 || edges.isEmpty()) {
            return Optional.empty();
        }
        for (MapEdge edge : edges) {
            UUID from = edge.getFromNode().getId();
            UUID to = edge.getToNode().getId();
            for (int i = 0; i < nodeIds.size() - 1; i++) {
                UUID a = nodeIds.get(i);
                UUID b = nodeIds.get(i + 1);
                if ((a.equals(from) && b.equals(to)) || (a.equals(to) && b.equals(from))) {
                    return Optional.of(edge);
                }
            }
        }
        return Optional.empty();
    }

    // 승인 시점 기준으로 그 재탐색이 제안한 경로가 현재 화재 구간을 지나는지 다시 확인한다.
    // trigger()/triggerForFireSpread() 호출 이후에도 화재가 더 번질 수 있어, 승인 직전에
    // 한 번 더 검증하지 않으면 관리자가 화재 통과 경로를 승인해버릴 수 있다.
    private boolean crossesCurrentFire(RouteRecalculation recalculation) {
        UUID floorId = recalculation.getTriggerEdge().getFloor().getId();
        List<UUID> firedCellIds = floorGridCellRepository.findAllByFloor_IdAndIsFiredTrue(floorId).stream()
                .map(FloorGridCell::getId)
                .toList();
        if (firedCellIds.isEmpty()) {
            return false;
        }
        List<MapEdge> firedEdges = mapEdgeGridCellRepository.findAllByGridCell_IdIn(firedCellIds).stream()
                .map(MapEdgeGridCell::getMapEdge)
                .distinct()
                .toList();
        return crossesAnyEdge(recalculation.getRecalculatedNodeIds(), firedEdges);
    }

    // VERY_CROWDED만 완전 제외한다 - 배율만으로는 다른 대안이 훨씬 나쁠 때 여전히 그 엣지를
    // 통과하는 경로가 선택될 수 있어, "사실상 통행 불가"를 표현하려면 그래프에서 아예 빼야 한다.
    private Set<UUID> excludedEdgesFor(List<MapEdge> affectedEdges, CongestionLevel level) {
        return level == CongestionLevel.VERY_CROWDED
                ? affectedEdges.stream().map(MapEdge::getId).collect(Collectors.toSet())
                : Set.of();
    }

    private Map<UUID, Double> weightMultipliersFor(List<MapEdge> affectedEdges, CongestionLevel level) {
        double multiplier = switch (level) {
            case CAUTION -> CAUTION_WEIGHT_MULTIPLIER;
            case CROWDED -> CROWDED_WEIGHT_MULTIPLIER;
            case NORMAL, VERY_CROWDED -> 1.0;
        };
        if (multiplier == 1.0) {
            return Map.of();
        }
        return affectedEdges.stream().collect(Collectors.toMap(
                MapEdge::getId, edge -> multiplier, (left, right) -> left));
    }

    // 혼잡 종료 시 정상(트리거 엣지를 포함한 직행) 경로로의 복구 후보를 계산한다.
    // 현재 활성 경로가 이미 그 엣지를 포함한 직행 경로라면(=승인된 우회가 없다면) 복구할 게 없으므로 아무것도 하지 않는다.
    private void triggerRecovery(TrainingSession session, MapEdge triggerEdge, CongestionLevel level,
            String cctvCode, double density) {
        Optional<RouteRecalculation> latestApproved = routeRecalculationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
                        session.getId(), RecalculationStatus.APPROVED);
        if (latestApproved.isEmpty()) {
            return;
        }
        RouteRecalculation activeDetour = latestApproved.get();

        UUID floorId = triggerEdge.getFloor().getId();
        MapNode representativeStart = session.getScenario().getStartNode();
        if (representativeStart == null) {
            log.warn("시나리오에 대표 startNode가 없어 재탐색 승인 대기 항목을 생성하지 않음: sessionId={}",
                    session.getId());
            return;
        }
        UUID startNodeId = representativeStart.getId();

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

    // "현재 활성 경로"를 별도로 저장하지 않으므로, 세션에서 가장 최근 승인된 경로가 있으면 그것을,
    // 없으면 대표 시작점 기준 정상(직행) 경로를 활성 경로로 취급한다.
    private RouteSnapshot resolveActiveRoute(TrainingSession session, UUID floorId, UUID startNodeId) {
        Optional<RouteRecalculation> latestApproved = routeRecalculationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
                        session.getId(), RecalculationStatus.APPROVED);
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
            RouteSnapshot previous, EvacuationRoute candidate, List<UUID> candidateNodeIds) {
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
        trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(trainingSessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
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

    // "지금 안내되고 있는 경로"를 세션 단위로 한 번에, 도면에 바로 그릴 수 있는 노드 상세
    // 목록으로 반환한다. resolveActiveRoute()와 동일한 우선순위(가장 최근 승인된 재탐색 >
    // 시나리오 대표 startNode 기준 최단 경로)를 쓰지만, 특정 triggerEdge에 종속되지 않고
    // 세션 전체에서 가장 최근 승인 건을 찾는다는 점이 다르다.
    // 세션 상태(RUNNING 여부)는 검증하지 않는다 - SCHEDULED 세션은 아직 재탐색이 없으므로
    // 자연히 최단 경로로, COMPLETED/ERROR 세션은 종료 시점까지의 마지막 승인 경로로 응답된다.
    @Transactional(readOnly = true)
    public CurrentRouteResponse getCurrentRoute(UUID sessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        TrainingSession session = trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
        TrainingScenario scenario = session.getScenario();

        Optional<RouteRecalculation> latestApproved = routeRecalculationRepository
                .findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(sessionId, RecalculationStatus.APPROVED);
        if (latestApproved.isPresent()) {
            RouteRecalculation approved = latestApproved.get();
            List<CurrentRouteResponse.NodePoint> path = toNodePoints(approved.getRecalculatedNodeIds());
            UUID startNodeId = path.isEmpty() ? null : path.get(0).nodeId();
            return new CurrentRouteResponse(
                    sessionId,
                    scenario.getId(),
                    scenario.getBuildingId(),
                    approved.getTriggerEdge().getFloor().getId(),
                    startNodeId,
                    CurrentRouteResponse.RouteSource.RECALCULATED,
                    path,
                    approved.getTotalWeight(),
                    approved.getResolvedAt());
        }

        MapNode representativeStart = scenario.getStartNode();
        if (representativeStart == null) {
            throw new ApiException(TrainingErrorCode.START_NODE_NOT_CONFIGURED);
        }
        UUID floorId = representativeStart.getFloor().getId();
        EvacuationRoute directRoute =
                evacuationRouteService.findShortestRoute(floorId, representativeStart.getId());
        List<CurrentRouteResponse.NodePoint> path = directRoute.path().stream()
                .map(CurrentRouteResponse.NodePoint::from)
                .toList();
        return new CurrentRouteResponse(
                sessionId,
                scenario.getId(),
                scenario.getBuildingId(),
                floorId,
                representativeStart.getId(),
                CurrentRouteResponse.RouteSource.INITIAL,
                path,
                directRoute.totalWeight(),
                session.getCreatedAt());
    }

    // RouteRecalculation은 노드 id만 저장하므로(@ElementCollection), 도면에 그릴 이름/타입/좌표를
    // 채우려면 MapNode를 다시 조회해야 한다. findAllById는 순서를 보장하지 않으므로 원래
    // 저장된 순서(출발 -> 도착)대로 재정렬한다.
    private List<CurrentRouteResponse.NodePoint> toNodePoints(List<UUID> nodeIds) {
        Map<UUID, MapNode> nodesById = mapNodeJpaRepository.findAllById(nodeIds).stream()
                .collect(Collectors.toMap(MapNode::getId, node -> node));
        return nodeIds.stream()
                .map(nodesById::get)
                .map(CurrentRouteResponse.NodePoint::from)
                .toList();
    }

    @Transactional
    public RouteRecalculationResponse approve(UUID recalculationId, String approverEmail) {
        String schoolName = schoolContextService.getSchoolName(approverEmail);
        UUID sessionId = routeRecalculationRepository
                .findTrainingSessionIdByIdAndSchoolName(recalculationId, schoolName)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND));
        trainingSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

        RouteRecalculation recalculation = routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(recalculationId, schoolName)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND));
        validatePending(recalculation);
        if (crossesCurrentFire(recalculation)) {
            throw new ApiException(EvacuationErrorCode.ROUTE_RECALCULATION_CROSSES_FIRE);
        }
        User approver = findUserOrThrow(approverEmail);
        List<RouteRecalculation> siblingPending = routeRecalculationRepository
                .findAllByTrainingSession_IdAndStatus(
                        sessionId, RecalculationStatus.PENDING);

        recalculation.approve(Instant.now(), approver);
        siblingPending.stream()
                .filter(pending -> !pending.getId().equals(recalculation.getId()))
                .forEach(pending -> cancel(pending, "다른 경로 승인으로 무효화됨"));
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
