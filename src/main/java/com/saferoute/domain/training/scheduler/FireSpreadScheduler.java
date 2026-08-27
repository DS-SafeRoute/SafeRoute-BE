package com.saferoute.domain.training.scheduler;

import com.saferoute.domain.training.service.FireSpreadService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// RUNNING 세션들의 화재 확산을 주기적으로 진행
// 실제 tick 여부(시나리오별 FireSpreadSpeed에 따른 간격, 최소 5초)는
// FireSpreadService가 세션 단위(lastSpreadAt 기준)로 판단하므로,
// 이 스캔 주기는 가장 빠른 속도(FAST=5초)보다 촘촘해야 tick을 놓치지 않는다.
@Component
@RequiredArgsConstructor
public class FireSpreadScheduler {

    private static final long SCAN_INTERVAL_MS = 2_000;

    private final FireSpreadService fireSpreadService;

    @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
    public void scanRunningSessions() {
        fireSpreadService.spreadAllRunningSessions();
    }
}
