package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

  @NotNull
  private TrainingStatus status;

  @NotNull
  private Instant startedAt;

  @NotNull
  private UUID adminId;
}
