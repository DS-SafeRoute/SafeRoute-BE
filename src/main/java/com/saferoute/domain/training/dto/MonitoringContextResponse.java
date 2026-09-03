package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.TrainingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "모니터링 상세 화면에 필요한 세션 기본 정보")
public record MonitoringContextResponse(
        @Schema(description = "조회한 훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @Schema(description = "시나리오명", example = "3학년 A동 화재 대피 훈련")
        String scenarioName,

        @Schema(description = "세션이 속한 건물명", example = "A동")
        String buildingName,

        @Schema(description = "세션 상태", example = "RUNNING")
        TrainingStatus status,

        @Schema(
                description = "훈련 시작 시각(Unix epoch milliseconds). 아직 시작 전(SCHEDULED)이면 null",
                example = "1787722000000",
                nullable = true
        )
        Long startedAt,

        @Schema(
                description = "훈련 종료 시각(Unix epoch milliseconds). 아직 종료되지 않았으면 null",
                example = "1787723000000",
                nullable = true
        )
        Long endedAt,

        @Schema(
                description = "경과 시간(초). RUNNING이면 현재 시각 기준으로 계속 늘어나는 값, "
                        + "종료된 세션이면 종료 시각 기준으로 고정된 값. 아직 시작 전(SCHEDULED)이면 null",
                example = "95",
                nullable = true
        )
        Long elapsedSeconds,

        @Schema(description = "Pi 관측 저장 간격(초). 전역 설정값", example = "5")
        int snapshotIntervalSec,

        @Schema(
                description = "CCTV 현재 상태(current-states)가 stale로 판정되는 기준(초). 전역 설정값",
                example = "15"
        )
        int stateStaleAfterSec
) {
}
