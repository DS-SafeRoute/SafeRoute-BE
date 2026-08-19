package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.congestion.dto.request.ReportCongestionRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.repository.MapEdgeJpaRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.floor.entity.Floor;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CongestionEventServiceTest {

    @InjectMocks
    private CongestionEventService congestionEventService;

    @Mock
    private MapEdgeJpaRepository mapEdgeJpaRepository;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    @Mock
    private RouteRecalculationService routeRecalculationService;

    private final UUID edgeId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private MapEdge edge;

    @BeforeEach
    void setUp() {
        Building building = mock(Building.class);
        org.mockito.Mockito.lenient().when(building.getId()).thenReturn(buildingId);

        Floor floor = mock(Floor.class);
        org.mockito.Mockito.lenient().when(floor.getBuilding()).thenReturn(building);

        edge = mock(MapEdge.class);
        org.mockito.Mockito.lenient().when(edge.getId()).thenReturn(edgeId);
        org.mockito.Mockito.lenient().when(edge.getFloor()).thenReturn(floor);
    }

    private ReportCongestionRequest request(CongestionLevel level) {
        return new ReportCongestionRequest(
                UUID.randomUUID(), sessionId, edgeId, "CCTV_001", 5.0, 8, 25, 2.5,
                level, 1_000L, 2_000L, 2_000L, 1L, null
        );
    }

    @Test
    @DisplayName("엣지를 찾을 수 없으면 MAP_EDGE_NOT_FOUND를 던진다")
    void reportCongestion_throwsWhenEdgeNotFound() {
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> congestionEventService.reportCongestion(request(CongestionLevel.NORMAL)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.MAP_EDGE_NOT_FOUND);
    }

    @Test
    @DisplayName("대상 건물에 RUNNING 세션이 없으면 전용 에러를 반환한다")
    void reportCongestion_throwsWhenNoRunningSession() {
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> congestionEventService.reportCongestion(request(CongestionLevel.CROWDED)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND
                );

        verify(observationRepository, never()).saveIfAbsent(any());
        verify(trainingEventPublisher, never()).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any());
    }

    @Test
    @DisplayName("NORMAL/CAUTION은 저장·발행만 하고 재탐색은 트리거하지 않는다")
    void reportCongestion_doesNotTriggerRecalculationForLowLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);

        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId
        )).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.created(invocation.getArgument(0, ObservationItem.class)));

        congestionEventService.reportCongestion(request(CongestionLevel.CAUTION));

        verify(observationRepository).saveIfAbsent(argThat(item ->
                item.getAvgHeadcount().equals(5.0)
                        && item.getSampleCount().equals(25)
                        && item.getDensity().equals(2.5)
                        && item.getExpiresAt() == 2_592_002L
        ));
        verify(trainingEventPublisher, org.mockito.Mockito.times(1)).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any());
    }

    @Test
    @DisplayName("CROWDED이면 저장·발행 후 재탐색을 트리거한다")
    void reportCongestion_triggersRecalculationForHighLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);

        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId
        )).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.created(invocation.getArgument(0, ObservationItem.class)));

        congestionEventService.reportCongestion(request(CongestionLevel.CROWDED));

        verify(routeRecalculationService, org.mockito.Mockito.times(1))
                .trigger(session, edge, CongestionLevel.CROWDED);
    }

    @Test
    @DisplayName("VERY_CROWDED이면 저장·발행 후 재탐색을 트리거한다")
    void reportCongestion_triggersRecalculationForVeryCrowdedLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId
        )).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.created(invocation.getArgument(0, ObservationItem.class)));

        congestionEventService.reportCongestion(request(CongestionLevel.VERY_CROWDED));

        verify(routeRecalculationService).trigger(session, edge, CongestionLevel.VERY_CROWDED);
    }

    @Test
    @DisplayName("중복 eventId이면 발행과 재탐색을 다시 수행하지 않는다")
    void reportCongestion_doesNotRepeatSideEffectsForDuplicateEvent() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(sessionId);
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                sessionId, TrainingStatus.RUNNING, buildingId
        )).willReturn(Optional.of(session));
        given(observationRepository.saveIfAbsent(any())).willAnswer(invocation ->
                IdempotentSaveResult.existing(invocation.getArgument(0, ObservationItem.class)));

        var result = congestionEventService.reportCongestion(request(CongestionLevel.CROWDED));

        assertThat(result.created()).isFalse();
        verify(trainingEventPublisher, never()).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any());
    }
}
