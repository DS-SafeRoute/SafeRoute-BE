package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "MonitoringEventListApiResponse",
        description = "이벤트 타임라인의 공통 API 응답 스키마"
)
public record MonitoringEventListApiResponse(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 코드", example = "TRAINING_SUCCESS_009")
        String code,

        @Schema(description = "응답 메시지", example = "모니터링 이벤트 타임라인 조회에 성공했습니다.")
        String message,

        @Schema(description = "이벤트 타임라인")
        MonitoringEventListResponse result
) {
}
