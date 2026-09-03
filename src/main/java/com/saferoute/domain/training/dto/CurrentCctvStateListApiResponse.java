package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "CurrentCctvStateListApiResponse",
        description = "CCTV별 현재 혼잡 상태 목록의 공통 API 응답 스키마"
)
public record CurrentCctvStateListApiResponse(
        @Schema(description = "요청 성공 여부", example = "true")
        boolean isSuccess,

        @Schema(description = "응답 코드", example = "TRAINING_SUCCESS_010")
        String code,

        @Schema(description = "응답 메시지", example = "CCTV 현재 혼잡 상태 조회에 성공했습니다.")
        String message,

        @Schema(description = "CCTV별 현재 혼잡 상태 목록")
        CurrentCctvStateListResponse result
) {
}
