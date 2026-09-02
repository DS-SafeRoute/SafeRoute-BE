package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.graph.repository.MapNodeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.dto.response.CurrentRouteResponse;
import com.saferoute.domain.evacuation.recalculation.dto.response.RouteRecalculationResponse;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationTriggerType;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RouteRecalculationServiceTest {

    private static final String MANAGER_EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private RouteRecalculationService routeRecalculationService;

    @Mock
    private RouteRecalculationRepository routeRecalculationRepository;

    @Mock
    private EvacuationRouteService evacuationRouteService;

    @Mock
    private IoTLightService ioTLightService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    @Mock
    private SchoolContextService schoolContextService;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private MapNodeJpaRepository mapNodeJpaRepository;

    private TrainingSession session;
    private TrainingScenario scenario;
    private MapEdge triggerEdge;
    private MapNode representativeStart;
    private UUID floorId;
    private UUID startNodeId;

    @BeforeEach
    void setUp() {
        session = mock(TrainingSession.class);
        org.mockito.Mockito.lenient().when(session.getId()).thenReturn(UUID.randomUUID());
        org.mockito.Mockito.lenient().when(schoolContextService.getSchoolName(MANAGER_EMAIL))
                .thenReturn(SCHOOL_NAME);

        Floor floor = mock(Floor.class);
        floorId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(floor.getId()).thenReturn(floorId);

        // 시나리오의 대표 startNode. 재탐색 계산은 이제 이 노드를 출발점으로 쓴다.
        representativeStart = MapNode.create(floor, "HALLWAY1", NodeType.HALLWAY, "HALLWAY1", 0, 0, false);
        startNodeId = UUID.randomUUID();
        ReflectionTestUtils.setField(representativeStart, "id", startNodeId);

        scenario = mock(TrainingScenario.class);
        org.mockito.Mockito.lenient().when(scenario.getStartNode()).thenReturn(representativeStart);
        org.mockito.Mockito.lenient().when(session.getScenario()).thenReturn(scenario);

        triggerEdge = MapEdge.create(floor, mock(MapNode.class), mock(MapNode.class), 5.0, true);
        ReflectionTestUtils.setField(triggerEdge, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(triggerEdge, "floor", floor);
    }

    private void givenNoExistingPending() {
        given(routeRecalculationRepository.findByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                any(), any(), any())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("다른 기관의 훈련 세션으로 재탐색 목록을 조회하면 세션 not-found를 반환한다")
    void getRecalculations_otherSchool_throwsTrainingSessionNotFound() {
        UUID sessionId = UUID.randomUUID();
        given(trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> routeRecalculationService
                .getRecalculations(sessionId, null, MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
    }

    private void givenNoApprovedHistory() {
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
                any(), any(), any())).willReturn(Optional.empty());
    }

    private void givenNoDirectRoute() {
        given(evacuationRouteService.findShortestRoute(floorId, startNodeId))
                .willThrow(new ApiException(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND));
    }

    @Test
    @DisplayName("같은 세션+엣지에 이미 같은 레벨의 PENDING이 있으면 새로 트리거하지 않는다")
    void trigger_skipsWhenSameLevelPendingExists() {
        RouteRecalculation existing = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository.findByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                session.getId(), triggerEdge.getId(), RecalculationStatus.PENDING))
                .willReturn(Optional.of(existing));

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.CROWDED,
                RecalculationTriggerType.LEVEL_UP, "CCTV_001", 3.5);

        verify(evacuationRouteService, never()).findShortestRoute(any(), any(), anySet());
        verify(routeRecalculationRepository, never()).save(any());
    }

    @Test
    @DisplayName("레벨이 바뀌었으면 기존 PENDING을 CANCELLED로 무효화하고 새로 계산한다")
    void trigger_cancelsAndRecreatesWhenLevelChanges() {
        RouteRecalculation existing = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository.findByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                session.getId(), triggerEdge.getId(), RecalculationStatus.PENDING))
                .willReturn(Optional.of(existing));
        givenNoApprovedHistory();
        givenNoDirectRoute();
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet(), any()))
                .willThrow(new ApiException(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND));

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.VERY_CROWDED,
                RecalculationTriggerType.LEVEL_UP, "CCTV_001", 5.5);

        assertThat(existing.getStatus()).isEqualTo(RecalculationStatus.CANCELLED);
        verify(trainingEventPublisher).publishRouteRecalculationCancelledAfterCommit(existing);
    }

    @Test
    @DisplayName("우회 경로가 없으면 로그만 남기고 승인 대기 항목을 만들지 않는다")
    void trigger_skipsWhenNoDetourRouteFound() {
        givenNoExistingPending();
        givenNoApprovedHistory();
        givenNoDirectRoute();
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet(), any()))
                .willThrow(new ApiException(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND));

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.CROWDED,
                RecalculationTriggerType.STARTED, "CCTV_001", 3.5);

        verify(routeRecalculationRepository, never()).save(any());
        verify(trainingEventPublisher, never()).publishRouteRecalculationRequestedAfterCommit(any());
    }

    @Test
    @DisplayName("VERY_CROWDED면 트리거 엣지를 완전히 제외하고 우회 경로를 계산한다")
    void trigger_veryCrowded_excludesTriggerEdgeEntirely() {
        givenNoExistingPending();
        givenNoApprovedHistory();
        givenNoDirectRoute();

        MapNode exitNode = MapNode.create(mock(Floor.class), "STAIR1", NodeType.STAIR, "STAIR1", 0, 0, true);
        ReflectionTestUtils.setField(exitNode, "id", UUID.randomUUID());
        EvacuationRoute route = new EvacuationRoute(List.of(exitNode), 12.5);
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet(), any())).willReturn(route);

        RouteRecalculation saved = pendingRecalculation(CongestionLevel.VERY_CROWDED);
        given(routeRecalculationRepository.save(any())).willReturn(saved);

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.VERY_CROWDED,
                RecalculationTriggerType.STARTED, "CCTV_001", 5.5);

        ArgumentCaptor<Set<UUID>> excludedEdgesCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Map<UUID, Double>> multipliersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(evacuationRouteService).findShortestRoute(
                any(), any(), excludedEdgesCaptor.capture(), multipliersCaptor.capture());
        assertThat(excludedEdgesCaptor.getValue()).containsExactly(triggerEdge.getId());
        assertThat(multipliersCaptor.getValue()).isEmpty();

        verify(routeRecalculationRepository, times(1)).save(any());
        verify(trainingEventPublisher, times(1)).publishRouteRecalculationRequestedAfterCommit(saved);
    }

    @Test
    @DisplayName("CROWDED면 트리거 엣지를 제외하지 않고 3배 가중치만 줘서 후보에 남긴다")
    void trigger_crowded_appliesWeightMultiplierInsteadOfExcluding() {
        givenNoExistingPending();
        givenNoApprovedHistory();
        givenNoDirectRoute();

        MapNode exitNode = MapNode.create(mock(Floor.class), "STAIR1", NodeType.STAIR, "STAIR1", 0, 0, true);
        ReflectionTestUtils.setField(exitNode, "id", UUID.randomUUID());
        EvacuationRoute route = new EvacuationRoute(List.of(exitNode), 12.5);
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet(), any())).willReturn(route);

        RouteRecalculation saved = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository.save(any())).willReturn(saved);

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.CROWDED,
                RecalculationTriggerType.STARTED, "CCTV_001", 3.5);

        ArgumentCaptor<Set<UUID>> excludedEdgesCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Map<UUID, Double>> multipliersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(evacuationRouteService).findShortestRoute(
                any(), any(), excludedEdgesCaptor.capture(), multipliersCaptor.capture());
        assertThat(excludedEdgesCaptor.getValue()).isEmpty();
        assertThat(multipliersCaptor.getValue()).containsEntry(triggerEdge.getId(), 3.0);

        verify(routeRecalculationRepository, times(1)).save(any());
        verify(trainingEventPublisher, times(1)).publishRouteRecalculationRequestedAfterCommit(saved);
    }

    @Test
    @DisplayName("ENDED인데 승인된 우회 경로가 없으면 복구할 게 없어 아무것도 하지 않는다")
    void trigger_ended_doesNothingWithoutApprovedDetour() {
        givenNoExistingPending();
        givenNoApprovedHistory();

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.NORMAL,
                RecalculationTriggerType.ENDED, "CCTV_001", 1.0);

        verify(routeRecalculationRepository, never()).save(any());
        verify(evacuationRouteService, never()).findShortestRoute(any(), any());
    }

    @Test
    @DisplayName("ENDED이고 승인된 우회 경로가 있으면 정상 경로로의 복구 후보를 PENDING으로 만든다")
    void trigger_ended_createsRecoveryCandidateWhenApprovedDetourExists() {
        givenNoExistingPending();
        RouteRecalculation approvedDetour = approvedRecalculation(List.of(UUID.randomUUID()), 20.0);
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
                session.getId(), triggerEdge.getId(), RecalculationStatus.APPROVED))
                .willReturn(Optional.of(approvedDetour));

        MapNode exitNode = MapNode.create(mock(Floor.class), "STAIR1", NodeType.STAIR, "STAIR1", 0, 0, true);
        ReflectionTestUtils.setField(exitNode, "id", UUID.randomUUID());
        EvacuationRoute directRoute = new EvacuationRoute(List.of(exitNode), 12.5);
        given(evacuationRouteService.findShortestRoute(floorId, startNodeId)).willReturn(directRoute);

        RouteRecalculation saved = pendingRecalculation(CongestionLevel.NORMAL);
        given(routeRecalculationRepository.save(any())).willReturn(saved);

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.NORMAL,
                RecalculationTriggerType.ENDED, "CCTV_001", 1.0);

        verify(routeRecalculationRepository, times(1)).save(any());
        verify(trainingEventPublisher, times(1)).publishRouteRecalculationRequestedAfterCommit(saved);
    }

    @Test
    @DisplayName("ENDED 복구 후보가 현재 활성 경로와 동일하면 새 승인 요청을 만들지 않는다")
    void trigger_ended_skipsWhenRecoveryMatchesActiveRoute() {
        givenNoExistingPending();
        UUID sharedNodeId = UUID.randomUUID();
        RouteRecalculation approvedDetour = approvedRecalculation(List.of(sharedNodeId), 20.0);
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndTriggerEdge_IdAndStatusOrderByResolvedAtDesc(
                session.getId(), triggerEdge.getId(), RecalculationStatus.APPROVED))
                .willReturn(Optional.of(approvedDetour));

        MapNode exitNode = MapNode.create(mock(Floor.class), "STAIR1", NodeType.STAIR, "STAIR1", 0, 0, true);
        ReflectionTestUtils.setField(exitNode, "id", sharedNodeId);
        EvacuationRoute directRoute = new EvacuationRoute(List.of(exitNode), 20.0);
        given(evacuationRouteService.findShortestRoute(floorId, startNodeId)).willReturn(directRoute);

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.NORMAL,
                RecalculationTriggerType.ENDED, "CCTV_001", 1.0);

        verify(routeRecalculationRepository, never()).save(any());
    }

    @Test
    @DisplayName("훈련 세션의 남은 PENDING을 일괄 CANCELLED 처리한다")
    void cancelAllPendingForSession_cancelsEachPending() {
        RouteRecalculation pendingA = pendingRecalculation(CongestionLevel.CROWDED);
        RouteRecalculation pendingB = pendingRecalculation(CongestionLevel.VERY_CROWDED);
        UUID sessionId = UUID.randomUUID();
        given(routeRecalculationRepository.findAllByTrainingSession_IdAndStatus(sessionId, RecalculationStatus.PENDING))
                .willReturn(List.of(pendingA, pendingB));

        routeRecalculationService.cancelAllPendingForSession(sessionId, "훈련 종료로 무효화됨");

        assertThat(pendingA.getStatus()).isEqualTo(RecalculationStatus.CANCELLED);
        assertThat(pendingB.getStatus()).isEqualTo(RecalculationStatus.CANCELLED);
        assertThat(pendingA.getCancelReason()).isEqualTo("훈련 종료로 무효화됨");
        verify(trainingEventPublisher, times(2)).publishRouteRecalculationCancelledAfterCommit(any());
    }

    private RouteRecalculation pendingRecalculation(CongestionLevel level) {
        RouteRecalculation recalculation = RouteRecalculation.createPending(
                session, triggerEdge, "CCTV_001", RecalculationTriggerType.STARTED, level, 3.5,
                List.of(UUID.randomUUID()), 10.0, List.of(UUID.randomUUID()), 12.5);
        ReflectionTestUtils.setField(recalculation, "id", UUID.randomUUID());
        return recalculation;
    }

    private RouteRecalculation approvedRecalculation(List<UUID> nodeIds, double totalWeight) {
        RouteRecalculation recalculation = RouteRecalculation.createPending(
                session, triggerEdge, "CCTV_001", RecalculationTriggerType.STARTED, CongestionLevel.CROWDED, 3.5,
                List.of(UUID.randomUUID()), 10.0, nodeIds, totalWeight);
        ReflectionTestUtils.setField(recalculation, "id", UUID.randomUUID());
        recalculation.approve(Instant.now(), mock(User.class));
        return recalculation;
    }

    @Test
    @DisplayName("존재하지 않는 재탐색 ID로 승인하면 ROUTE_RECALCULATION_NOT_FOUND를 던진다")
    void approve_whenNotFound_throws() {
        UUID recalculationId = UUID.randomUUID();
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculationId, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> routeRecalculationService.approve(recalculationId, MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.ROUTE_RECALCULATION_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING이 아니면 승인 시 INVALID_RECALCULATION_STATUS_TRANSITION을 던진다")
    void approve_whenNotPending_throws() {
        RouteRecalculation recalculation = pendingRecalculation(CongestionLevel.CROWDED);
        recalculation.approve(Instant.now(), mock(User.class));
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculation.getId(), SCHOOL_NAME)).willReturn(Optional.of(recalculation));

        assertThatThrownBy(() -> routeRecalculationService.approve(recalculation.getId(), MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION);

        verify(trainingEventPublisher, never()).publishEvacuationRouteUpdatedAfterCommit(any());
    }

    @Test
    @DisplayName("승인자를 찾을 수 없으면 ADMIN_NOT_FOUND를 던진다")
    void approve_whenApproverMissing_throws() {
        RouteRecalculation recalculation = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculation.getId(), SCHOOL_NAME)).willReturn(Optional.of(recalculation));
        given(userRepository.findByEmail(MANAGER_EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> routeRecalculationService.approve(recalculation.getId(), MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TrainingErrorCode.ADMIN_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING이면 승인 시 상태를 APPROVED로 바꾸고 WS 발행 및 유도등 반영을 한다")
    void approve_whenPending_succeedsAndPublishes() {
        RouteRecalculation recalculation = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculation.getId(), SCHOOL_NAME)).willReturn(Optional.of(recalculation));
        User manager = mock(User.class);
        org.mockito.Mockito.lenient().when(manager.getUsername()).thenReturn("manager");
        given(userRepository.findByEmail(MANAGER_EMAIL)).willReturn(Optional.of(manager));

        RouteRecalculationResponse response = routeRecalculationService.approve(recalculation.getId(), MANAGER_EMAIL);

        assertThat(response.status()).isEqualTo(RecalculationStatus.APPROVED);
        assertThat(recalculation.getStatus()).isEqualTo(RecalculationStatus.APPROVED);
        assertThat(recalculation.getResolvedAt()).isNotNull();
        assertThat(recalculation.getResolvedBy()).isEqualTo(manager);
        verify(trainingEventPublisher, times(1)).publishEvacuationRouteUpdatedAfterCommit(recalculation);
        verify(ioTLightService, times(1)).applyRouteGuidance(recalculation.getRecalculatedNodeIds());
    }

    @Test
    @DisplayName("PENDING이 아니면 거절 시 INVALID_RECALCULATION_STATUS_TRANSITION을 던진다")
    void reject_whenNotPending_throws() {
        RouteRecalculation recalculation = pendingRecalculation(CongestionLevel.CROWDED);
        recalculation.reject(Instant.now(), mock(User.class), null);
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculation.getId(), SCHOOL_NAME)).willReturn(Optional.of(recalculation));

        assertThatThrownBy(() -> routeRecalculationService.reject(recalculation.getId(), MANAGER_EMAIL, "사유"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.INVALID_RECALCULATION_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("PENDING이면 거절 시 상태를 REJECTED로 바꾸고 사유를 저장한다")
    void reject_whenPending_succeedsWithoutEvacuationUpdate() {
        RouteRecalculation recalculation = pendingRecalculation(CongestionLevel.CROWDED);
        given(routeRecalculationRepository
                .findByIdAndTrainingSession_Scenario_Building_SchoolName(
                        recalculation.getId(), SCHOOL_NAME)).willReturn(Optional.of(recalculation));
        User manager = mock(User.class);
        given(userRepository.findByEmail(MANAGER_EMAIL)).willReturn(Optional.of(manager));

        RouteRecalculationResponse response =
                routeRecalculationService.reject(recalculation.getId(), MANAGER_EMAIL, "현장 확인 결과 통행 가능");

        assertThat(response.status()).isEqualTo(RecalculationStatus.REJECTED);
        assertThat(recalculation.getStatus()).isEqualTo(RecalculationStatus.REJECTED);
        assertThat(recalculation.getRejectReason()).isEqualTo("현장 확인 결과 통행 가능");
        verify(trainingEventPublisher, never()).publishEvacuationRouteUpdatedAfterCommit(any());
        verify(trainingEventPublisher, times(1)).publishRouteRecalculationRejectedAfterCommit(recalculation);
        verify(ioTLightService, never()).applyRouteGuidance(any());
    }

    // === getCurrentRoute ===

    @Test
    @DisplayName("다른 기관의 훈련 세션으로 현재 경로를 조회하면 세션 not-found를 반환한다")
    void getCurrentRoute_otherSchool_throwsTrainingSessionNotFound() {
        UUID otherSessionId = UUID.randomUUID();
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(otherSessionId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> routeRecalculationService.getCurrentRoute(otherSessionId, MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
    }

    @Test
    @DisplayName("승인된 재탐색이 있으면 그 경로를 노드 상세와 함께 현재 경로로 반환한다")
    void getCurrentRoute_withApprovedRecalculation_returnsApprovedRoute() {
        UUID recSessionId = session.getId();
        UUID scenarioId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(recSessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getBuildingId()).willReturn(buildingId);

        MapNode stairNode = mock(MapNode.class);
        UUID stairNodeId = UUID.randomUUID();
        given(stairNode.getId()).willReturn(stairNodeId);
        given(stairNode.getName()).willReturn("서측 계단");
        given(stairNode.getType()).willReturn(NodeType.STAIR);
        given(stairNode.getX()).willReturn(0.5);
        given(stairNode.getY()).willReturn(0.4);

        MapNode exitNode = mock(MapNode.class);
        UUID exitNodeId = UUID.randomUUID();
        given(exitNode.getId()).willReturn(exitNodeId);
        given(exitNode.getName()).willReturn("1층 출입구");
        given(exitNode.getType()).willReturn(NodeType.EXIT);
        given(exitNode.getX()).willReturn(0.8);
        given(exitNode.getY()).willReturn(0.3);

        List<UUID> recalculatedIds = List.of(startNodeId, stairNodeId, exitNodeId);
        RouteRecalculation approved = approvedRecalculation(recalculatedIds, 18.3);
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
                recSessionId, RecalculationStatus.APPROVED))
                .willReturn(Optional.of(approved));
        // findAllById가 저장 순서를 보장하지 않는다는 걸 검증하기 위해 일부러 뒤섞어 반환한다.
        given(mapNodeJpaRepository.findAllById(recalculatedIds))
                .willReturn(List.of(exitNode, representativeStart, stairNode));

        CurrentRouteResponse response = routeRecalculationService.getCurrentRoute(recSessionId, MANAGER_EMAIL);

        assertThat(response.sessionId()).isEqualTo(recSessionId);
        assertThat(response.scenarioId()).isEqualTo(scenarioId);
        assertThat(response.buildingId()).isEqualTo(buildingId);
        assertThat(response.floorId()).isEqualTo(floorId);
        assertThat(response.startNodeId()).isEqualTo(startNodeId);
        assertThat(response.source()).isEqualTo(CurrentRouteResponse.RouteSource.RECALCULATED);
        assertThat(response.path()).extracting(CurrentRouteResponse.NodePoint::nodeId)
                .containsExactly(startNodeId, stairNodeId, exitNodeId);
        assertThat(response.path().get(1).name()).isEqualTo("서측 계단");
        assertThat(response.path().get(2).type()).isEqualTo(NodeType.EXIT);
        assertThat(response.totalWeight()).isEqualTo(18.3);
        assertThat(response.updatedAt()).isEqualTo(approved.getResolvedAt());
    }

    @Test
    @DisplayName("SCHEDULED 세션에 승인된 재탐색이 없으면 대표 START 기준 INITIAL 경로를 반환한다")
    void getCurrentRoute_scheduledWithoutApprovedRecalculation_returnsInitialRoute() {
        UUID recSessionId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        TrainingSession scheduledSession = TrainingSession.schedule(mock(User.class), scenario);
        ReflectionTestUtils.setField(scheduledSession, "id", recSessionId);
        ReflectionTestUtils.setField(scheduledSession, "createdAt", createdAt);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(recSessionId, SCHOOL_NAME))
                .willReturn(Optional.of(scheduledSession));
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
                recSessionId, RecalculationStatus.APPROVED))
                .willReturn(Optional.empty());
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getBuildingId()).willReturn(buildingId);

        MapNode exitNode = mock(MapNode.class);
        UUID exitNodeId = UUID.randomUUID();
        given(exitNode.getId()).willReturn(exitNodeId);
        given(exitNode.getName()).willReturn("1층 출입구");
        given(exitNode.getType()).willReturn(NodeType.EXIT);
        given(exitNode.getX()).willReturn(0.8);
        given(exitNode.getY()).willReturn(0.3);

        given(evacuationRouteService.findShortestRoute(floorId, startNodeId))
                .willReturn(new EvacuationRoute(List.of(representativeStart, exitNode), 9.5));

        CurrentRouteResponse response = routeRecalculationService.getCurrentRoute(recSessionId, MANAGER_EMAIL);

        assertThat(response.sessionId()).isEqualTo(recSessionId);
        assertThat(response.scenarioId()).isEqualTo(scenarioId);
        assertThat(response.buildingId()).isEqualTo(buildingId);
        assertThat(response.floorId()).isEqualTo(floorId);
        assertThat(response.startNodeId()).isEqualTo(startNodeId);
        assertThat(response.source()).isEqualTo(CurrentRouteResponse.RouteSource.INITIAL);
        assertThat(response.path()).extracting(CurrentRouteResponse.NodePoint::nodeId)
                .containsExactly(startNodeId, exitNodeId);
        assertThat(response.totalWeight()).isEqualTo(9.5);
        assertThat(response.updatedAt()).isEqualTo(createdAt);
        assertThat(scheduledSession.getStatus()).isEqualTo(TrainingStatus.SCHEDULED);
        assertThat(scheduledSession.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("승인된 재탐색도 없고 대표 startNode도 없으면 START_NODE_NOT_CONFIGURED를 던진다")
    void getCurrentRoute_noApprovedAndNoStartNode_throws() {
        UUID recSessionId = session.getId();
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(recSessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(routeRecalculationRepository.findFirstByTrainingSession_IdAndStatusOrderByResolvedAtDesc(
                recSessionId, RecalculationStatus.APPROVED))
                .willReturn(Optional.empty());
        given(scenario.getStartNode()).willReturn(null);

        assertThatThrownBy(() -> routeRecalculationService.getCurrentRoute(recSessionId, MANAGER_EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.START_NODE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("시나리오에 대표 startNode가 없으면 재탐색 승인 대기 항목을 만들지 않는다")
    void trigger_noRepresentativeStartNode_doesNothing() {
        givenNoExistingPending();
        given(scenario.getStartNode()).willReturn(null);

        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.CROWDED,
                RecalculationTriggerType.STARTED, "CCTV_001", 3.5);

        verify(routeRecalculationRepository, never()).save(any());
        verify(evacuationRouteService, never()).findShortestRoute(any(), any(), anySet(), any());
    }
}
