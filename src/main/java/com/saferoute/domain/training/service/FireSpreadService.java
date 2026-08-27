package com.saferoute.domain.training.service;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 스케줄러 진입점 - RUNNING 세션 전체를 순회하며 각각 독립적으로 확산
@Slf4j
@Service
@RequiredArgsConstructor
public class FireSpreadService {

    private final TrainingSessionRepository sessionRepository;
    private final FireSpreadStepService fireSpreadStepService;

    public void spreadAllRunningSessions() {
        List<TrainingSession> running = sessionRepository.findAllByStatus(TrainingStatus.RUNNING);
        for (TrainingSession session : running) {
            try {
                fireSpreadStepService.spreadOneStep(session.getId());
            } catch (Exception e) {
                // 한 세션의 실패가 다른 세션의 확산을 막지 않도록 개별 격리
                log.error("화재 확산 처리 실패, sessionId={}", session.getId(), e);
            }
        }
    }
}
