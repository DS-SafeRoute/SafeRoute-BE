package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "TrainingSessionListApiResponse",
        description = "훈련 세션 목록의 공통 API 응답 스키마"
)
public record TrainingSessionListApiResponse(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 코드", example = "TRAINING_SUCCESS_008")
        String code,

        @Schema(description = "응답 메시지", example = "훈련 세션 목록 조회에 성공했습니다.")
        String message,

        @Schema(description = "훈련 세션 목록")
        TrainingSessionListResponse result
) {
}
