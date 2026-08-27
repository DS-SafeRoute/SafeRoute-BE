package com.saferoute.domain.training.service;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
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
import java.util.UUID;
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
    }

    private Duration tickIntervalOf(FireSpreadSpeed speed) {
        return switch (speed) {
            case FAST -> Duration.ofSeconds(5);
            case MEDIUM -> Duration.ofSeconds(15);
            case SLOW -> Duration.ofSeconds(30);
        };
    }
}
