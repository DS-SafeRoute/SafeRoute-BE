package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
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
}
