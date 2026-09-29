package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FireSpreadStepServiceTest {

    @InjectMocks
    private FireSpreadStepService fireSpreadStepService;

    @Mock
    private TrainingSessionRepository sessionRepository;

    @Mock
    private FireZoneRepository fireZoneRepository;

    @Mock
    private FloorGridCellRepository gridCellRepository;

    @Mock
    private MapEdgeGridCellRepository mapEdgeGridCellRepository;

    @Mock
    private RouteRecalculationService routeRecalculationService;

    @Mock
    private TrainingEventPublisher eventPublisher;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID scenarioId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    private FloorGridCell cellAt(Floor floor, int row, int col, boolean walkable) {
        FloorGridCell cell = FloorGridCell.create(floor, row, col, walkable, 0.0, 0.0);
        ReflectionTestUtils.setField(cell, "id", UUID.randomUUID());
        return cell;
    }

    private TrainingSession sessionWith(Instant lastSpreadAt, int currentGeneration, TrainingScenario scenario) {
        TrainingSession session = TrainingSession.create(TrainingStatus.RUNNING, lastSpreadAt, mock(
                com.saferoute.domain.user.entity.User.class), scenario);
        ReflectionTestUtils.setField(session, "currentGeneration", currentGeneration);
        return session;
    }

    @Test
    @DisplayName("마지막 확산 이후 시나리오 속도의 tick 간격이 지나지 않았으면 아무 것도 하지 않는다")
    void spreadOneStep_intervalNotElapsed_doesNothing() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST); // 5초 간격
        TrainingSession session = sessionWith(Instant.now(), 0, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);

        fireSpreadStepService.spreadOneStep(sessionId);

        verify(fireZoneRepository, never()).findByScenario_IdAndSpreadGeneration(any(), anyInt());
        verify(eventPublisher, never()).publishFireSpreadUpdatedAfterCommit(any(), anyInt(), any());
    }

    @Test
    @DisplayName("lastSpreadAt이 null이어도 startedAt을 기준으로 간격을 판단해 NPE 없이 동작한다")
    void spreadOneStep_lastSpreadAtNull_fallsBackToStartedAtWithoutNpe() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        // lastSpreadAt=null인 세션 (예: 이 방어 코드가 없던 시절 생성된 RUNNING 세션)
        TrainingSession session = TrainingSession.create(
                TrainingStatus.RUNNING, Instant.now().minusSeconds(10), mock(com.saferoute.domain.user.entity.User.class), scenario);
        ReflectionTestUtils.setField(session, "lastSpreadAt", null);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 0)).willReturn(List.of());

        fireSpreadStepService.spreadOneStep(sessionId);

        verify(fireZoneRepository).findByScenario_IdAndSpreadGeneration(scenarioId, 0);
    }

    @Test
    @DisplayName("tick 간격이 지나면 프론티어의 인접 셀 중 walkable하고 아직 안 붙은 셀에만 불이 옮겨붙고 세대가 1 증가한다")
    void spreadOneStep_intervalElapsed_spreadsToWalkableUnfiredNeighborsOnly() {
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST); // 5초 간격

        // 마지막 확산이 10초 전이라 5초 간격을 이미 지났다 -> 이번 tick에서 확산되어야 한다
        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 0, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);

        FloorGridCell originCell = cellAt(floor, 1, 1, true);
        FireZone origin = FireZone.createOrigin(scenario, floor, originCell);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 0))
                .willReturn(List.of(origin));

        FloorGridCell walkableUnfired = cellAt(floor, 1, 2, true);
        FloorGridCell alreadyFired = cellAt(floor, 1, 0, true);
        alreadyFired.markFired();
        FloorGridCell notWalkable = cellAt(floor, 0, 1, false);
        given(gridCellRepository.findAdjacent(floorId, 1, 1))
                .willReturn(List.of(walkableUnfired, alreadyFired, notWalkable));

        fireSpreadStepService.spreadOneStep(sessionId);

        assertThat(walkableUnfired.isFired()).isTrue();
        assertThat(notWalkable.isFired()).isFalse();
        assertThat(session.getCurrentGeneration()).isEqualTo(1);

        ArgumentCaptor<List<FireZone>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(fireZoneRepository).saveAll(savedCaptor.capture());
        List<FireZone> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getGridCellId()).isEqualTo(walkableUnfired.getId());
        assertThat(saved.get(0).getSpreadGeneration()).isEqualTo(1);

        verify(eventPublisher).publishFireSpreadUpdatedAfterCommit(sessionId, 1, saved);
    }

    @Test
    @DisplayName("현재 세대의 프론티어가 비어있으면(더 이상 번질 곳이 없으면) 세대를 진행시키지 않는다")
    void spreadOneStep_emptyFrontier_doesNotAdvanceGeneration() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 3, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 3))
                .willReturn(List.of());

        fireSpreadStepService.spreadOneStep(sessionId);

        assertThat(session.getCurrentGeneration()).isEqualTo(3);
        verify(fireZoneRepository, never()).saveAll(any());
        verify(eventPublisher, never()).publishFireSpreadUpdatedAfterCommit(any(), anyInt(), any());
    }

    @Test
    @DisplayName("새로 옮겨붙은 셀이 있으면 그 층의 전체 화재 셀 기준으로 영향받는 엣지를 모아 경로 재탐색을 트리거한다")
    void spreadOneStep_intervalElapsed_triggersRouteRecalculationWithAllFiredEdges() {
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 0, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);

        FloorGridCell originCell = cellAt(floor, 1, 1, true);
        originCell.markFired();
        FireZone origin = FireZone.createOrigin(scenario, floor, originCell);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 0))
                .willReturn(List.of(origin));

        FloorGridCell newlyFiredCell = cellAt(floor, 1, 2, true);
        given(gridCellRepository.findAdjacent(floorId, 1, 1))
                .willReturn(List.of(newlyFiredCell));

        // 이번 틱 이전부터 불이 붙어있던 셀 + 이번 틱에 새로 옮겨붙은 셀, 둘 다 조회되어야 한다(누적).
        given(gridCellRepository.findAllByFloor_IdAndIsFiredTrue(floorId))
                .willReturn(List.of(originCell, newlyFiredCell));
        // 이 시나리오가 그 층에 낸 FireZone(누적 발화 셀)으로도 잡혀야, 다른 시나리오의 화재와
        // 섞이지 않는다는 필터를 통과해 위 두 셀이 실제로 반영된다.
        FireZone spreadZone = FireZone.createSpread(scenario, floor, newlyFiredCell, 1);
        given(fireZoneRepository.findByScenario_IdAndFloor_Id(scenarioId, floorId))
                .willReturn(List.of(origin, spreadZone));

        MapEdge previouslyFiredEdge = MapEdge.create(floor, mock(MapNode.class), mock(MapNode.class), 3.0, true);
        ReflectionTestUtils.setField(previouslyFiredEdge, "id", UUID.randomUUID());
        MapEdge newlyFiredEdge = MapEdge.create(floor, mock(MapNode.class), mock(MapNode.class), 3.0, true);
        ReflectionTestUtils.setField(newlyFiredEdge, "id", UUID.randomUUID());
        MapEdgeGridCell mapping1 = MapEdgeGridCell.create(previouslyFiredEdge, originCell);
        MapEdgeGridCell mapping2 = MapEdgeGridCell.create(newlyFiredEdge, newlyFiredCell);
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any()))
                .willReturn(List.of(mapping1, mapping2));

        fireSpreadStepService.spreadOneStep(sessionId);

        ArgumentCaptor<List<MapEdge>> affectedEdgesCaptor = ArgumentCaptor.forClass(List.class);
        verify(routeRecalculationService).triggerForFireSpread(eq(session), affectedEdgesCaptor.capture());
        assertThat(affectedEdgesCaptor.getValue())
                .containsExactlyInAnyOrder(previouslyFiredEdge, newlyFiredEdge);
    }

    @Test
    @DisplayName("새로 옮겨붙은 셀이 없으면(더 이상 번질 곳이 없으면) 경로 재탐색을 트리거하지 않는다")
    void spreadOneStep_emptyFrontier_doesNotTriggerRouteRecalculation() {
        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 3, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 3))
                .willReturn(List.of());

        fireSpreadStepService.spreadOneStep(sessionId);

        verify(routeRecalculationService, never()).triggerForFireSpread(any(), any());
    }

    @Test
    @DisplayName("프론티어는 있지만 인접 셀이 모두 이미 발화했거나 통행 불가라 새로 옮겨붙은 셀이 없으면 재탐색을 트리거하지 않는다")
    void spreadOneStep_noNewlyFiredNeighbors_doesNotTriggerRouteRecalculation() {
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 0, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);

        FloorGridCell originCell = cellAt(floor, 1, 1, true);
        FireZone origin = FireZone.createOrigin(scenario, floor, originCell);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 0))
                .willReturn(List.of(origin));

        FloorGridCell alreadyFired = cellAt(floor, 1, 0, true);
        alreadyFired.markFired();
        FloorGridCell notWalkable = cellAt(floor, 0, 1, false);
        given(gridCellRepository.findAdjacent(floorId, 1, 1))
                .willReturn(List.of(alreadyFired, notWalkable));

        fireSpreadStepService.spreadOneStep(sessionId);

        verify(routeRecalculationService, never()).triggerForFireSpread(any(), any());
        verify(gridCellRepository, never()).findAllByFloor_IdAndIsFiredTrue(any());
    }

    @Test
    @DisplayName("새로 옮겨붙은 셀이 있어도 그 층의 발화 셀에 걸린 엣지가 없으면 재탐색을 트리거하지 않는다")
    void spreadOneStep_newlyFiredCellsWithoutConnectedEdges_doesNotTriggerRouteRecalculation() {
        Floor floor = mock(Floor.class);
        given(floor.getId()).willReturn(floorId);

        TrainingScenario scenario = mock(TrainingScenario.class);
        given(scenario.getId()).willReturn(scenarioId);
        given(scenario.getFireSpreadSpeed()).willReturn(FireSpreadSpeed.FAST);

        TrainingSession session = sessionWith(Instant.now().minusSeconds(10), 0, scenario);
        given(sessionRepository.getReferenceById(sessionId)).willReturn(session);

        FloorGridCell originCell = cellAt(floor, 1, 1, true);
        originCell.markFired();
        FireZone origin = FireZone.createOrigin(scenario, floor, originCell);
        given(fireZoneRepository.findByScenario_IdAndSpreadGeneration(scenarioId, 0))
                .willReturn(List.of(origin));

        FloorGridCell newlyFiredCell = cellAt(floor, 1, 2, true);
        given(gridCellRepository.findAdjacent(floorId, 1, 1))
                .willReturn(List.of(newlyFiredCell));
        given(gridCellRepository.findAllByFloor_IdAndIsFiredTrue(floorId))
                .willReturn(List.of(originCell, newlyFiredCell));
        FireZone spreadZone = FireZone.createSpread(scenario, floor, newlyFiredCell, 1);
        given(fireZoneRepository.findByScenario_IdAndFloor_Id(scenarioId, floorId))
                .willReturn(List.of(origin, spreadZone));
        given(mapEdgeGridCellRepository.findAllByGridCell_IdIn(any())).willReturn(List.of());

        fireSpreadStepService.spreadOneStep(sessionId);

        verify(routeRecalculationService, never()).triggerForFireSpread(any(), any());
    }
}
