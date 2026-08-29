package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "MonitoringCameraListApiResponse",
        description = "카메라별 최신 캡처 목록의 공통 API 응답 스키마"
)
public record MonitoringCameraListApiResponse(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 코드", example = "TRAINING_SUCCESS_006")
        String code,

        @Schema(description = "응답 메시지", example = "모니터링 카메라 목록 조회에 성공했습니다.")
        String message,

        @Schema(description = "카메라별 최신 캡처 목록")
        MonitoringCameraListResponse result
) {
}
