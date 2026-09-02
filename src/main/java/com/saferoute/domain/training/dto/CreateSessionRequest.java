package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

  @Schema(
      description = "세션을 생성할 관리자 ID",
      example = "8d40b5e1-40f8-4dd4-a11c-f1ed418b73d1",
      requiredMode = Schema.RequiredMode.REQUIRED
  )
  @NotNull
  private UUID adminId;
}
