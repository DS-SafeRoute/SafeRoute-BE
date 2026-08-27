package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "훈련 세션 목록의 항목 하나")
public record TrainingSessionSummaryResponse(
        @Schema(description = "훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @Schema(description = "훈련 시나리오 이름", example = "3학년 A동 화재 대피 훈련")
        String scenarioName,

        @Schema(description = "훈련이 진행되는 건물 ID", example = "b5a6e5b0-1e3a-4b8a-9b8a-6a2b6b1f5a11")
        UUID buildingId,

        @Schema(description = "훈련이 진행되는 건물명", example = "A동")
        String buildingName,

        @Schema(description = "훈련 세션 상태", example = "RUNNING")
        TrainingStatus status,

        @Schema(description = "훈련 시작 시각", example = "2026-08-26T05:26:00Z")
        Instant startedAt
) {

    public static TrainingSessionSummaryResponse from(TrainingSession session) {
        return new TrainingSessionSummaryResponse(
                session.getId(),
                session.getScenario().getName(),
                session.getScenario().getBuilding().getId(),
                session.getScenario().getBuilding().getName(),
                session.getStatus(),
                session.getStartedAt()
        );
    }
}
