package com.saferoute.domain.training.scheduler;

import com.saferoute.domain.training.service.TrainingSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// RUNNING 세션 중 10분 하드 타임아웃(TrainingSessionService.TRAINING_TIMEOUT)을 넘긴 세션을
// 주기적으로 스캔해 FAILED 처리한다. 텔레메트리 기반 3분 공백 정밀 판정은 카메라 연동 후 별도 구현.
@Component
@RequiredArgsConstructor
public class TrainingTimeoutScheduler {

  private static final long SCAN_INTERVAL_MS = 30_000;

  private final TrainingSessionService trainingSessionService;

  @Scheduled(fixedDelay = SCAN_INTERVAL_MS)
  public void scanTimedOutSessions() {
    trainingSessionService.failTimedOutSessions();
  }
}
