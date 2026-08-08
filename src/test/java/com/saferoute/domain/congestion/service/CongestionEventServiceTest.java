package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import com.saferoute.domain.telemetry.dynamo.repository.CongestionSummaryRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
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
    private CongestionSummaryRepository congestionSummaryRepository;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    @Mock
    private RouteRecalculationService routeRecalculationService;

    private final UUID edgeId = UUID.randomUUID();
    private final UUID buildingId = UUID.randomUUID();
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
        return new ReportCongestionRequest(edgeId, "CCTV_001", 5, 8, level, 1000L, 2000L, null);
    }

    @Test
    @DisplayName("엣지를 찾을 수 없으면 MAP_EDGE_NOT_FOUND를 던진다")
    void reportCongestion_throwsWhenEdgeNotFound() {
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> congestionEventService.reportCongestion(request(CongestionLevel.LOW)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", EvacuationErrorCode.MAP_EDGE_NOT_FOUND);
    }

    @Test
    @DisplayName("대상 건물에 RUNNING 세션이 없으면 저장/발행/트리거 없이 조용히 종료한다")
    void reportCongestion_noOpWhenNoRunningSession() {
        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId))
                .willReturn(Optional.empty());

        congestionEventService.reportCongestion(request(CongestionLevel.HIGH));

        verify(congestionSummaryRepository, never()).save(any());
        verify(trainingEventPublisher, never()).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any());
    }

    @Test
    @DisplayName("LOW/MEDIUM은 저장·발행만 하고 재탐색은 트리거하지 않는다")
    void reportCongestion_doesNotTriggerRecalculationForLowLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(UUID.randomUUID());

        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId))
                .willReturn(Optional.of(session));

        congestionEventService.reportCongestion(request(CongestionLevel.MEDIUM));

        verify(congestionSummaryRepository, org.mockito.Mockito.times(1)).save(any());
        verify(trainingEventPublisher, org.mockito.Mockito.times(1)).publishCongestionUpdated(any(), any(), any());
        verify(routeRecalculationService, never()).trigger(any(), any(), any());
    }

    @Test
    @DisplayName("HIGH/CRITICAL이면 저장·발행 후 재탐색을 트리거한다")
    void reportCongestion_triggersRecalculationForHighLevel() {
        TrainingSession session = mock(TrainingSession.class);
        given(session.getId()).willReturn(UUID.randomUUID());

        given(mapEdgeJpaRepository.findById(edgeId)).willReturn(Optional.of(edge));
        given(trainingSessionRepository.findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(TrainingStatus.RUNNING, buildingId))
                .willReturn(Optional.of(session));

        congestionEventService.reportCongestion(request(CongestionLevel.CRITICAL));

        verify(routeRecalculationService, org.mockito.Mockito.times(1))
                .trigger(session, edge, CongestionLevel.CRITICAL);
    }
}
