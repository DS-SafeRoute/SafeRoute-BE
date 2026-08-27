package com.saferoute.domain.training.service;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.repository.FloorGridCellRepository;
import com.saferoute.domain.training.entity.FireSpreadSpeed;
import com.saferoute.domain.training.entity.FireZone;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.FireZoneRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// RUNNING 세션의 화재를 인접 셀로 한 세대씩 확산
// FireZone.spreadGeneration으로 확산을 추적해 매 tick마다 마지막 세대의 셀들만 조회
@Slf4j
@Service
@RequiredArgsConstructor
public class FireSpreadService {

    private final TrainingSessionRepository sessionRepository;
    private final FireZoneRepository fireZoneRepository;
    private final FloorGridCellRepository gridCellRepository;
    private final TrainingEventPublisher eventPublisher;

    // 스케줄러 진입점 - RUNNING 세션 전체를 순회하며 각각 독립적으로 확산 시도
    public void spreadAllRunningSessions() {
        List<TrainingSession> running = sessionRepository.findAllByStatus(TrainingStatus.RUNNING);
        for (TrainingSession session : running) {
            try {
                spreadOneStep(session.getId());
            } catch (Exception e) {
                // 한 세션의 실패가 다른 세션의 확산을 막지 않도록 개별 격리
                log.error("화재 확산 처리 실패, sessionId={}", session.getId(), e);
            }
        }
    }

    // 세션 하나의 확산을 1스텝 진행
    @Transactional
    public void spreadOneStep(UUID sessionId) {
        TrainingSession session = sessionRepository.getReferenceById(sessionId);

        Duration interval = tickIntervalOf(session.getScenario().getFireSpreadSpeed());
        if (Duration.between(session.getLastSpreadAt(), Instant.now()).compareTo(interval) < 0) {
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

        eventPublisher.publishFireSpreadUpdated(sessionId, nextGen, newlyFired);
    }

    private Duration tickIntervalOf(FireSpreadSpeed speed) {
        return switch (speed) {
            case FAST -> Duration.ofSeconds(5);
            case MEDIUM -> Duration.ofSeconds(15);
            case SLOW -> Duration.ofSeconds(30);
        };
    }
}
