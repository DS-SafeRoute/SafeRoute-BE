package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "MonitoringContextApiResponse",
        description = "모니터링 세션 정보의 공통 API 응답 스키마"
)
public record MonitoringContextApiResponse(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 코드", example = "TRAINING_SUCCESS_011")
        String code,

        @Schema(description = "응답 메시지", example = "모니터링 세션 정보 조회에 성공했습니다.")
        String message,

        @Schema(description = "모니터링 세션 정보")
        MonitoringContextResponse result
) {
}
