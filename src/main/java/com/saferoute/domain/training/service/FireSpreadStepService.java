package com.saferoute.domain.training.service;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.evacuation.grid.repository.MapEdgeGridCellRepository;
import com.saferoute.domain.evacuation.recalculation.service.RouteRecalculationService;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 세션 하나의 화재 확산을 1스텝 진행한다.
@Service
@RequiredArgsConstructor
public class FireSpreadStepService {

    private final TrainingSessionRepository sessionRepository;
    private final FireZoneRepository fireZoneRepository;
    private final FloorGridCellRepository gridCellRepository;
    private final MapEdgeGridCellRepository mapEdgeGridCellRepository;
    private final RouteRecalculationService routeRecalculationService;
    private final TrainingEventPublisher eventPublisher;

    @Transactional
    public void spreadOneStep(UUID sessionId) {
        TrainingSession session = sessionRepository.getReferenceById(sessionId);

        Duration interval = tickIntervalOf(session.getScenario().getFireSpreadSpeed());

        Instant lastSpreadAt = session.getLastSpreadAt() != null ? session.getLastSpreadAt() : session.getStartedAt();
        if (Duration.between(lastSpreadAt, Instant.now()).compareTo(interval) < 0) {
            return;
        }

        UUID scenarioId = session.getScenario().getId();
        int currentGen = session.getCurrentGeneration();

        List<FireZone> frontier = fireZoneRepository
                .findByScenario_IdAndSpreadGeneration(scenarioId, currentGen);

        if (frontier.isEmpty()) {
            return; // 더 이상 번질 곳 없음
        }

        int nextGen = currentGen + 1;
        List<FireZone> newlyFired = new ArrayList<>();

        for (FireZone fz : frontier) {
            FloorGridCell cell = fz.getGridCell();
            for (FloorGridCell neighbor : gridCellRepository
                    .findAdjacent(cell.getFloor().getId(), cell.getRowIndex(), cell.getColumnIndex())) {
                if (neighbor.isWalkable() && !neighbor.isFired()) {
                    neighbor.markFired();
                    newlyFired.add(FireZone.createSpread(fz.getScenario(), fz.getFloor(), neighbor, nextGen));
                }
            }
        }

        if (!newlyFired.isEmpty()) {
            fireZoneRepository.saveAll(newlyFired);
        }
        session.advanceSpread(nextGen, Instant.now());

        eventPublisher.publishFireSpreadUpdatedAfterCommit(sessionId, nextGen, newlyFired);

        triggerRouteRecalculationForNewlyFired(session, newlyFired);
    }

    // 새로 옮겨붙은 셀이 걸린 층마다, "현재 그 층에서 불이 붙은 모든 셀"을 기준으로 영향받는
    // MapEdge를 다시 구해 재탐색을 트리거한다. 이번 틱에 새로 옮겨붙은 셀만 넘기면 이전 틱에
    // 이미 제외됐던 구간이 이번 후보 경로에 다시 섞여 들어갈 수 있으므로 반드시 누적 상태로 조회한다.
    private void triggerRouteRecalculationForNewlyFired(TrainingSession session, List<FireZone> newlyFired) {
        Set<UUID> affectedFloorIds = newlyFired.stream()
                .map(FireZone::getFloorId)
                .collect(Collectors.toSet());

        for (UUID floorId : affectedFloorIds) {
            List<UUID> firedCellIds = gridCellRepository.findAllByFloor_IdAndIsFiredTrue(floorId).stream()
                    .map(FloorGridCell::getId)
                    .toList();
            if (firedCellIds.isEmpty()) {
                continue;
            }
            List<MapEdge> affectedEdges = mapEdgeGridCellRepository.findAllByGridCell_IdIn(firedCellIds).stream()
                    .map(MapEdgeGridCell::getMapEdge)
                    .distinct()
                    .toList();
            if (!affectedEdges.isEmpty()) {
                routeRecalculationService.triggerForFireSpread(session, affectedEdges);
            }
        }
    }

    private Duration tickIntervalOf(FireSpreadSpeed speed) {
        return switch (speed) {
            case FAST -> Duration.ofSeconds(5);
            case MEDIUM -> Duration.ofSeconds(15);
            case SLOW -> Duration.ofSeconds(30);
        };
    }
}
