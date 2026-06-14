package com.saferoute.domain.training.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ScheduledSessionResponse implements TrainingStatusResponse {
  private String buildingName;
  private Integer totalFloors;
  private Instant scheduledAt;
  private Integer expectedParticipants;
}