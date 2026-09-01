package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.device.service.IoTLightService;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.TrainingSessionListResponse;
import com.saferoute.domain.training.dto.TrainingSessionSummaryResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TrainingSessionServiceTest {

    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private TrainingSessionService trainingSessionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private TrainingScenarioRepository trainingScenarioRepository;

    @Mock
    private FireZoneRepository fireZoneRepository;

    @Mock
    private RouteRecalculationService routeRecalculationService;

    @Mock
    private EvacuationRouteService evacuationRouteService;

    @Mock
    private IoTLightService ioTLightService;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    @Mock
    private SchoolContextService schoolContextService;

    @Mock
    private BuildingRepository buildingRepository;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();

    @BeforeEach
    void setUpSchoolContext() {
        org.mockito.Mockito.lenient()
                .when(schoolContextService.getSchoolName(EMAIL))
                .thenReturn(SCHOOL_NAME);
    }

    private TrainingSession sessionWithStatus(TrainingStatus status) {
        TrainingScenario scenario = mock(TrainingScenario.class);
        org.mockito.Mockito.lenient().when(scenario.getBuildingId()).thenReturn(buildingId);
        TrainingSession session = TrainingSession.create(status, Instant.now(), mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        return session;
    }

    @Test
    @DisplayName("다른 기관의 훈련 세션 상태는 조회할 수 없다")
    void getTrainingStatus_otherSchool_throwsNotFound() {
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> trainingSessionService.getTrainingStatus(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
    }

    // === getSessions ===

    @Test
    @DisplayName("요청자 학교의 상태별 세션 목록을 최신순으로 반환한다")
    void getSessions_returnsSummariesForRequesterSchool() {
        UUID buildingId = UUID.randomUUID();
        Building building = mock(Building.class);
        given(building.getId()).willReturn(buildingId);
        given(building.getName()).willReturn("A동");
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getName()).willReturn("3학년 A동 화재 대피 훈련");
        given(scenario.getBuilding()).willReturn(building);
        Instant startedAt = Instant.parse("2026-08-26T05:26:00Z");
        TrainingSession session =
                TrainingSession.create(TrainingStatus.RUNNING, startedAt, mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        given(trainingSessionRepository
                .findAllByStatusAndScenario_Building_SchoolNameOrderByStartedAtDesc(
                        TrainingStatus.RUNNING, SCHOOL_NAME))
                .willReturn(List.of(session));

        TrainingSessionListResponse response = trainingSessionService.getSessions(TrainingStatus.RUNNING, EMAIL);

        assertThat(response.sessions()).hasSize(1);
        TrainingSessionSummaryResponse summary = response.sessions().get(0);
        assertThat(summary.sessionId()).isEqualTo(sessionId);
        assertThat(summary.scenarioName()).isEqualTo("3학년 A동 화재 대피 훈련");
        assertThat(summary.buildingId()).isEqualTo(buildingId);
        assertThat(summary.buildingName()).isEqualTo("A동");
        assertThat(summary.status()).isEqualTo(TrainingStatus.RUNNING);
        assertThat(summary.startedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("해당 상태의 세션이 없으면 빈 목록을 반환한다")
    void getSessions_noMatchingSessions_returnsEmptyList() {
        given(trainingSessionRepository
                .findAllByStatusAndScenario_Building_SchoolNameOrderByStartedAtDesc(
                        TrainingStatus.RUNNING, SCHOOL_NAME))
                .willReturn(List.of());

        TrainingSessionListResponse response = trainingSessionService.getSessions(TrainingStatus.RUNNING, EMAIL);

        assertThat(response.sessions()).isEmpty();
    }

    // === create ===

    @Test
    @DisplayName("RUNNING 상태로 세션을 생성할 때 시작 시각이 없으면 예외가 발생한다")
    void create_runningWithoutStartedAt_throwsException() {
        UUID adminId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();

        User manager = mock(User.class);
        given(manager.getRole()).willReturn(UserRole.MANAGER);
        given(userRepository.findByIdAndSchoolName(adminId, SCHOOL_NAME)).willReturn(Optional.of(manager));
        given(trainingScenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(mock(TrainingScenario.class)));

        CreateSessionRequest request = new CreateSessionRequest(TrainingStatus.RUNNING, null, adminId);

        assertThatThrownBy(() -> trainingSessionService.create(request, scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("이미 세션이 존재하는 시나리오로는 세션을 또 생성할 수 없다")
    void create_scenarioAlreadyHasSession_throwsException() {
        UUID adminId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        User manager = mock(User.class);
        given(manager.getRole()).willReturn(UserRole.MANAGER);
        given(userRepository.findByIdAndSchoolName(adminId, SCHOOL_NAME)).willReturn(Optional.of(manager));
        given(trainingScenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(mock(TrainingScenario.class)));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(true);

        CreateSessionRequest request = new CreateSessionRequest(TrainingStatus.SCHEDULED, null, adminId);

        assertThatThrownBy(() -> trainingSessionService.create(request, scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.SESSION_ALREADY_EXISTS);
        verify(trainingSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("existsByScenario_Id 통과 이후 동시 요청으로 인한 UNIQUE 제약 위반은 SESSION_ALREADY_EXISTS로 변환된다")
    void create_concurrentInsertViolatesUniqueConstraint_throwsSessionAlreadyExists() {
        UUID adminId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        User manager = mock(User.class);
        given(manager.getRole()).willReturn(UserRole.MANAGER);
        given(userRepository.findByIdAndSchoolName(adminId, SCHOOL_NAME)).willReturn(Optional.of(manager));
        given(trainingScenarioRepository.findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.of(mock(TrainingScenario.class)));
        given(trainingSessionRepository.existsByScenario_Id(scenarioId)).willReturn(false);
        given(trainingSessionRepository.saveAndFlush(any()))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("unique constraint"));

        CreateSessionRequest request = new CreateSessionRequest(TrainingStatus.SCHEDULED, null, adminId);

        assertThatThrownBy(() -> trainingSessionService.create(request, scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.SESSION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("다른 기관의 시나리오로 훈련 세션을 생성할 수 없다")
    void create_otherSchoolScenario_throwsNotFound() {
        UUID adminId = UUID.randomUUID();
        UUID scenarioId = UUID.randomUUID();
        User manager = mock(User.class);
        given(manager.getRole()).willReturn(UserRole.MANAGER);
        given(userRepository.findByIdAndSchoolName(adminId, SCHOOL_NAME))
                .willReturn(Optional.of(manager));
        given(trainingScenarioRepository
                .findByIdAndBuilding_SchoolName(scenarioId, SCHOOL_NAME))
                .willReturn(Optional.empty());
        CreateSessionRequest request =
                new CreateSessionRequest(TrainingStatus.SCHEDULED, null, adminId);

        assertThatThrownBy(() -> trainingSessionService.create(request, scenarioId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND);
        verify(trainingSessionRepository, never()).save(any());
    }

    // === start ===

    @Test
    @DisplayName("SCHEDULED 상태의 세션을 시작하면 RUNNING으로 전이하고 최초 경로를 유도등에 반영한다")
    void start_fromScheduled_transitionsToRunningAndAppliesRouteGuidance() {
        UUID floorId = UUID.randomUUID();
        UUID startNodeId = UUID.randomUUID();
        UUID exitNodeId = UUID.randomUUID();
        MapNode startNode = mock(MapNode.class);
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);
        given(startNode.getFloor()).willReturn(floor);
        given(startNode.getId()).willReturn(startNodeId);
        MapNode exitNode = mock(MapNode.class);
        given(exitNode.getId()).willReturn(exitNodeId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getStartNode()).willReturn(startNode);
        TrainingSession session =
                TrainingSession.create(TrainingStatus.SCHEDULED, Instant.now(), mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));
        given(evacuationRouteService.findShortestRoute(floorId, startNodeId))
                .willReturn(new EvacuationRoute(List.of(startNode, exitNode), 12.0));

        trainingSessionService.start(sessionId, EMAIL);

        assertThat(session.getStatus()).isEqualTo(TrainingStatus.RUNNING);
        verify(trainingEventPublisher, times(1)).publishTrainingStatusUpdatedAfterCommit(session);
        verify(scenario, times(1)).markInProgress();
        verify(ioTLightService).applyRouteGuidance(List.of(startNodeId, exitNodeId));
    }

    @Test
    @DisplayName("같은 건물에 RUNNING 세션이 있으면 SCHEDULED 세션을 시작할 수 없다")
    void start_buildingAlreadyHasRunningSession_throwsConflict() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession session =
                TrainingSession.create(TrainingStatus.SCHEDULED, Instant.now(), mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME))
                .willReturn(Optional.of(session));
        given(trainingSessionRepository.existsByStatusAndScenario_Building_Id(
                TrainingStatus.RUNNING, buildingId)).willReturn(true);

        assertThatThrownBy(() -> trainingSessionService.start(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.RUNNING_SESSION_ALREADY_EXISTS);

        assertThat(session.getStatus()).isEqualTo(TrainingStatus.SCHEDULED);
        verify(evacuationRouteService, never()).findShortestRoute(any(), any());
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    @Test
    @DisplayName("시작 노드가 지정되지 않은 시나리오는 훈련을 시작할 수 없다")
    void start_startNodeNotConfigured_throwsExceptionWithoutChangingState() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getStartNode()).willReturn(null);
        TrainingSession session =
                TrainingSession.create(TrainingStatus.SCHEDULED, Instant.now(), mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> trainingSessionService.start(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.START_NODE_NOT_CONFIGURED);
        assertThat(session.getStatus()).isEqualTo(TrainingStatus.SCHEDULED);
        verify(scenario, never()).markInProgress();
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
        verify(ioTLightService, never()).applyRouteGuidance(any());
    }

    @Test
    @DisplayName("출발 노드에서 경로를 찾지 못하면 훈련 시작이 차단되고 상태가 바뀌지 않는다")
    void start_routeNotFound_throwsExceptionWithoutChangingState() {
        UUID floorId = UUID.randomUUID();
        UUID startNodeId = UUID.randomUUID();
        MapNode startNode = mock(MapNode.class);
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);
        given(startNode.getFloor()).willReturn(floor);
        given(startNode.getId()).willReturn(startNodeId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getStartNode()).willReturn(startNode);
        TrainingSession session =
                TrainingSession.create(TrainingStatus.SCHEDULED, Instant.now(), mock(User.class), scenario);
        ReflectionTestUtils.setField(session, "id", sessionId);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));
        given(evacuationRouteService.findShortestRoute(floorId, startNodeId))
                .willThrow(new ApiException(com.saferoute.global.api.error.EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND));

        assertThatThrownBy(() -> trainingSessionService.start(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(com.saferoute.global.api.error.EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND);
        assertThat(session.getStatus()).isEqualTo(TrainingStatus.SCHEDULED);
        verify(scenario, never()).markInProgress();
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
        verify(ioTLightService, never()).applyRouteGuidance(any());
    }

    @Test
    @DisplayName("이미 RUNNING인 세션은 다시 시작할 수 없다")
    void start_alreadyRunning_throwsException() {
        TrainingSession session = sessionWithStatus(TrainingStatus.RUNNING);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> trainingSessionService.start(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.INVALID_STATUS_TRANSITION);
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    @Test
    @DisplayName("존재하지 않는 세션을 시작하려 하면 예외가 발생한다")
    void start_sessionNotFound_throwsException() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> trainingSessionService.start(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
    }

    // === end ===

    @Test
    @DisplayName("RUNNING 상태의 세션을 정상 종료하면 COMPLETED로 전이하고 이벤트를 발행하며 시나리오도 COMPLETED로 바뀐다")
    void end_fromRunning_transitionsToCompletedAndPublishesEvent() {
        TrainingSession session = sessionWithStatus(TrainingStatus.RUNNING);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        trainingSessionService.end(sessionId, EMAIL);

        assertThat(session.getStatus()).isEqualTo(TrainingStatus.COMPLETED);
        assertThat(session.getEndedAt()).isNotNull();
        verify(trainingEventPublisher, times(1)).publishTrainingStatusUpdatedAfterCommit(session);
        verify(session.getScenario(), times(1)).markCompleted();
        verify(ioTLightService).resetToNormal(buildingId);
    }

    @Test
    @DisplayName("이미 종료된 훈련은 정상 종료할 수 없다")
    void end_alreadyCompleted_throwsException() {
        TrainingSession session = sessionWithStatus(TrainingStatus.COMPLETED);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> trainingSessionService.end(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.INVALID_STATUS_TRANSITION);
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    @Test
    @DisplayName("존재하지 않는 세션을 정상 종료하려 하면 예외가 발생한다")
    void end_sessionNotFound_throwsException() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> trainingSessionService.end(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    // === forceEnd ===

    @Test
    @DisplayName("RUNNING 상태의 세션을 강제 종료하면 STOPPED로 전이하고 이벤트를 발행하며 시나리오는 ERROR로 바뀐다")
    void forceEnd_fromRunning_transitionsToStoppedAndPublishesEvent() {
        TrainingSession session = sessionWithStatus(TrainingStatus.RUNNING);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        trainingSessionService.forceEnd(sessionId, EMAIL);

        assertThat(session.getStatus()).isEqualTo(TrainingStatus.STOPPED);
        verify(trainingEventPublisher, times(1)).publishTrainingStatusUpdatedAfterCommit(session);
        verify(session.getScenario(), times(1)).markError();
        verify(ioTLightService).resetToNormal(buildingId);
    }

    @Test
    @DisplayName("이미 종료된 훈련은 강제종료할 수 없다")
    void forceEnd_alreadyStopped_throwsException() {
        TrainingSession session = sessionWithStatus(TrainingStatus.STOPPED);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> trainingSessionService.forceEnd(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.INVALID_STATUS_TRANSITION);
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    @Test
    @DisplayName("존재하지 않는 세션을 강제종료하려 하면 예외가 발생한다")
    void forceEnd_sessionNotFound_throwsException() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> trainingSessionService.forceEnd(sessionId, EMAIL))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getErrorCode())
                .isEqualTo(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND);
        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }

    // === failTimedOutSessions ===

    @Test
    @DisplayName("10분 타임아웃을 넘긴 RUNNING 세션을 FAILED로 처리하고 이벤트를 발행하며 시나리오는 ERROR로 바뀐다")
    void failTimedOutSessions_marksExpiredSessionsAsFailed() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getBuildingId()).willReturn(buildingId);
        TrainingSession timedOut = TrainingSession.create(
                TrainingStatus.RUNNING,
                Instant.now().minus(11, ChronoUnit.MINUTES),
                mock(User.class),
                scenario);
        ReflectionTestUtils.setField(timedOut, "id", sessionId);

        given(trainingSessionRepository.findByStatusAndStartedAtBefore(any(), any()))
                .willReturn(List.of(timedOut));

        trainingSessionService.failTimedOutSessions();

        assertThat(timedOut.getStatus()).isEqualTo(TrainingStatus.FAILED);
        verify(trainingEventPublisher, times(1)).publishTrainingStatusUpdatedAfterCommit(timedOut);
        verify(timedOut.getScenario(), times(1)).markError();
        verify(ioTLightService).resetToNormal(buildingId);
    }

    @Test
    @DisplayName("타임아웃된 세션이 없으면 아무 것도 하지 않는다")
    void failTimedOutSessions_noExpiredSessions_doesNothing() {
        given(trainingSessionRepository.findByStatusAndStartedAtBefore(any(), any()))
                .willReturn(List.of());

        trainingSessionService.failTimedOutSessions();

        verify(trainingEventPublisher, never()).publishTrainingStatusUpdatedAfterCommit(any());
    }
}
