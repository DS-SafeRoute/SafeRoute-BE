package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.entity.TrainingSession;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TrainingSessionResponse {
  private TrainingStatus status;
  private Instant startedAt;
  private String adminName;
  private String scenarioName;

  public static TrainingSessionResponse from(TrainingSession session) {
    return TrainingSessionResponse.builder()
        .status(session.getStatus())
        .startedAt(session.getStartedAt())
        .adminName(session.getAdmin().getUsername())
        .scenarioName(session.getScenario().getName())
        .build();
  }
}
