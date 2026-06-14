package com.saferoute.domain.training.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RunningSessionResponse implements TrainingStatusResponse {
  private String buildingName;
  private Long elapsedSeconds;
  private Integer actualParticipants;
  private BigDecimal currentSurvivalRate;
}
