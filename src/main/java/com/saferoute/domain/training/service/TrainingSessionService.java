package com.saferoute.domain.training.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.RunningSessionResponse;
import com.saferoute.domain.training.dto.ScheduledSessionResponse;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.dto.TrainingStatusResponse;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.entity.UserRole;
import com.saferoute.domain.user.repository.UserRepository;
import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingSessionService {

  // 텔레메트리(personCount) 수신 파이프라인이 아직 없어, 3분 공백 기반 정밀 타임아웃 판정 대신
  // 1단계로 RUNNING 세션에 대한 단순 하드 타임아웃만 적용한다. 카메라 연동 완료 후 별도 이슈에서 대체 예정.
  public static final Duration TRAINING_TIMEOUT = Duration.ofMinutes(10);

  private final UserRepository userRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final TrainingScenarioRepository trainingScenarioRepository;
  private final TrainingEventPublisher trainingEventPublisher;

  public TrainingSessionResponse create(CreateSessionRequest request, UUID scenarioId) {
    User user = userRepository.findById(request.getAdminId())
        .orElseThrow(() -> new ApiException(TrainingErrorCode.ADMIN_NOT_FOUND));
    if (user.getRole() != UserRole.MANAGER) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    TrainingScenario scenario = trainingScenarioRepository.findById(scenarioId)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SCENARIO_NOT_FOUND));

    TrainingSession trainingSession = TrainingSession.create(
        request.getStatus(),
        request.getStartedAt(),
        user,
        scenario
    );

    return TrainingSessionResponse.from(trainingSessionRepository.save(trainingSession));
  }

  @Transactional(readOnly = true)
  public TrainingStatusResponse getTrainingStatus(UUID sessionId) {
    TrainingSession session = findSession(sessionId);

    TrainingScenario scenario = session.getScenario();
    Building building = scenario.getBuilding();

    if (session.getStatus() == TrainingStatus.SCHEDULED) {
      return new ScheduledSessionResponse(
          building.getName(),
          building.getTotalFloors(),
          scenario.getScheduledAt(),
          scenario.getExpectedParticipants()
      );
    } else if (session.getStatus() == TrainingStatus.RUNNING) {
      long elapsed = Instant.now().getEpochSecond() - session.getStartedAt().getEpochSecond();
      return new RunningSessionResponse(
          building.getName(),
          elapsed,
          session.getActualParticipants() != null ? session.getActualParticipants() : 0,
          session.getCurrentSurvivalRate() != null ? session.getCurrentSurvivalRate() : BigDecimal.ZERO
      );
    }
    throw new ApiException(TrainingErrorCode.UNSUPPORTED_STATUS);
  }

  @Transactional
  public TrainingSessionResponse start(UUID sessionId) {
    TrainingSession session = findSession(sessionId);

    if (session.getStatus() != TrainingStatus.SCHEDULED) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    session.start(Instant.now());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  @Transactional
  public TrainingSessionResponse end(UUID sessionId) {
    TrainingSession session = findSession(sessionId);

    if (session.getStatus() != TrainingStatus.RUNNING) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    session.complete(Instant.now());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  @Transactional
  public TrainingSessionResponse forceEnd(UUID sessionId) {
    TrainingSession session = findSession(sessionId);

    if (session.getStatus() != TrainingStatus.RUNNING) {
      throw new ApiException(TrainingErrorCode.INVALID_STATUS_TRANSITION);
    }

    session.stop(Instant.now());
    trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);

    return TrainingSessionResponse.from(session);
  }

  // 10분 하드 타임아웃을 넘긴 RUNNING 세션을 스케줄러가 주기적으로 호출해 FAILED 처리한다.
  @Transactional
  public void failTimedOutSessions() {
    Instant threshold = Instant.now().minus(TRAINING_TIMEOUT);
    List<TrainingSession> timedOutSessions =
        trainingSessionRepository.findByStatusAndStartedAtBefore(TrainingStatus.RUNNING, threshold);

    for (TrainingSession session : timedOutSessions) {
      session.fail(Instant.now());
      trainingEventPublisher.publishTrainingStatusUpdatedAfterCommit(session);
      log.info("훈련 세션 타임아웃 처리: sessionId={}", session.getId());
    }
  }

  private TrainingSession findSession(UUID sessionId) {
    return trainingSessionRepository.findById(sessionId)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
  }
}
