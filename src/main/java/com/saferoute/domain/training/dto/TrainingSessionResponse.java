package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.entity.TrainingSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "훈련 세션 생성/시작/종료/강제종료 API의 공통 응답")
public class TrainingSessionResponse {
  @Schema(description = "세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
  private UUID id;

  @Schema(description = "세션 상태", example = "COMPLETED")
  private TrainingStatus status;

  @Schema(description = "훈련 시작 시각. 아직 시작되지 않았으면(SCHEDULED) null", nullable = true)
  private Instant startedAt;

  @Schema(
      description = "훈련 종료 시각(정상 종료 또는 강제 종료). RUNNING이거나 아직 "
          + "시작되지 않았으면 null. end/force-end 응답에는 방금 종료 처리된 시각이 그대로 담깁니다.",
      nullable = true
  )
  private Instant endedAt;

  @Schema(description = "세션을 생성한 관리자 이름", example = "박현지")
  private String adminName;

  @Schema(description = "훈련 시나리오명", example = "3학년 A동 화재 대피 훈련")
  private String scenarioName;

  public static TrainingSessionResponse from(TrainingSession session) {
    return TrainingSessionResponse.builder()
        .id(session.getId())
        .status(session.getStatus())
        .startedAt(session.getStartedAt())
        .endedAt(session.getEndedAt())
        .adminName(session.getAdmin().getUsername())
        .scenarioName(session.getScenario().getName())
        .build();
  }
}
