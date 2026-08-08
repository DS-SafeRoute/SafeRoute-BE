package com.saferoute.domain.evacuation.recalculation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.telemetry.dynamo.repository.TrainingEventRepository;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.util.List;
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

    @InjectMocks
    private RouteRecalculationService routeRecalculationService;

    @Mock
    private RouteRecalculationRepository routeRecalculationRepository;

    @Mock
    private EvacuationRouteService evacuationRouteService;

    @Mock
    private TrainingEventRepository trainingEventRepository;

    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    private TrainingSession session;
    private MapEdge triggerEdge;

    @BeforeEach
    void setUp() {
        session = mock(TrainingSession.class);
        given(session.getId()).willReturn(UUID.randomUUID());

        Floor floor = mock(Floor.class);
        org.mockito.Mockito.lenient().when(floor.getId()).thenReturn(UUID.randomUUID());

        MapNode fromNode = MapNode.create(floor, "HALLWAY1", NodeType.HALLWAY, "HALLWAY1", 0, 0, false);
        ReflectionTestUtils.setField(fromNode, "id", UUID.randomUUID());

        triggerEdge = MapEdge.create(floor, fromNode, mock(MapNode.class), 5.0, true);
        ReflectionTestUtils.setField(triggerEdge, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(triggerEdge, "floor", floor);
    }

    @Test
    @DisplayName("같은 세션+엣지에 이미 PENDING이 있으면 새로 트리거하지 않는다")
    void trigger_skipsWhenPendingAlreadyExists() {
        // given
        given(routeRecalculationRepository.existsByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                session.getId(), triggerEdge.getId(), RecalculationStatus.PENDING)).willReturn(true);

        // when
        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.HIGH);

        // then
        verify(evacuationRouteService, never()).findShortestRoute(any(), any(), anySet());
        verify(routeRecalculationRepository, never()).save(any());
    }

    @Test
    @DisplayName("우회 경로가 없으면 로그만 남기고 승인 대기 항목을 만들지 않는다")
    void trigger_skipsWhenNoDetourRouteFound() {
        // given
        given(routeRecalculationRepository.existsByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                any(), any(), any())).willReturn(false);
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet()))
                .willThrow(new ApiException(EvacuationErrorCode.EVACUATION_ROUTE_NOT_FOUND));

        // when
        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.HIGH);

        // then
        verify(routeRecalculationRepository, never()).save(any());
        verify(trainingEventRepository, never()).save(any());
        verify(trainingEventPublisher, never()).publishRouteRecalculationRequestedAfterCommit(any());
    }

    @Test
    @DisplayName("우회 경로를 찾으면 PENDING 저장 + DynamoDB 이력 저장 + WS 발행을 한다")
    void trigger_createsPendingRecalculationAndPublishesEvent() {
        // given
        MapNode exitNode = MapNode.create(mock(Floor.class), "STAIR1", NodeType.STAIR, "STAIR1", 0, 0, true);
        ReflectionTestUtils.setField(exitNode, "id", UUID.randomUUID());
        EvacuationRoute route = new EvacuationRoute(List.of(exitNode), 12.5);

        given(routeRecalculationRepository.existsByTrainingSession_IdAndTriggerEdge_IdAndStatus(
                any(), any(), any())).willReturn(false);
        given(evacuationRouteService.findShortestRoute(any(), any(), anySet())).willReturn(route);

        RouteRecalculation saved = RouteRecalculation.createPending(
                session, triggerEdge, CongestionLevel.HIGH, List.of(exitNode.getId()), 12.5);
        given(routeRecalculationRepository.save(any())).willReturn(saved);

        // when
        routeRecalculationService.trigger(session, triggerEdge, CongestionLevel.HIGH);

        // then
        ArgumentCaptor<Set<UUID>> excludedEdgesCaptor = ArgumentCaptor.forClass(Set.class);
        verify(evacuationRouteService).findShortestRoute(any(), any(), excludedEdgesCaptor.capture());
        assertThat(excludedEdgesCaptor.getValue()).containsExactly(triggerEdge.getId());

        verify(routeRecalculationRepository, times(1)).save(any());
        verify(trainingEventRepository, times(1)).save(any());
        verify(trainingEventPublisher, times(1)).publishRouteRecalculationRequestedAfterCommit(saved);
    }
}
