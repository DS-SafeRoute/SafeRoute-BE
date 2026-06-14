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
import com.saferoute.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

  private final UserRepository userRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final TrainingScenarioRepository trainingScenarioRepository;

  public TrainingSessionResponse create(CreateSessionRequest request, UUID scenarioId) {
    User user = userRepository.findById(request.getAdminId())
        .orElseThrow(NoSuchElementException::new);
    TrainingScenario scenario = trainingScenarioRepository.findById(scenarioId).orElseThrow(
        NoSuchElementException::new);

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
    TrainingSession session = trainingSessionRepository.findById(sessionId).orElseThrow(
        NoSuchElementException::new);

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
    throw new IllegalStateException("지원하지 않는 상태입니다: " + session.getStatus());
  }
}