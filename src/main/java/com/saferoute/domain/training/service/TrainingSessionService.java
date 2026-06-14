package com.saferoute.domain.training.service;

import com.saferoute.domain.training.dto.CreateSessionRequest;
import com.saferoute.domain.training.dto.TrainingSessionResponse;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingScenarioRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.entity.User;
import com.saferoute.domain.user.repository.UserRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingSessionService {

  private final UserRepository userRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final TrainingScenarioRepository trainingScenarioRepository;

  public TrainingSessionResponse create(CreateSessionRequest request, UUID scenarioId) {
    User user = userRepository.findById(request.getAdminId()).orElseThrow(NoSuchElementException::new);
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
}
