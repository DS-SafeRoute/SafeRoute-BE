package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "훈련 세션의 CCTV별 현재 혼잡 상태 목록")
public record CurrentCctvStateListResponse(
        @Schema(description = "조회한 훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @Schema(description = "이 응답을 만든 시각(Unix epoch milliseconds)", example = "1787722095000")
        long observedAt,

        @ArraySchema(
                arraySchema = @Schema(description = "활성 CCTV별 현재 상태 목록. CCTV가 없으면 빈 배열"),
                schema = @Schema(implementation = CurrentCctvStateResponse.class)
        )
        List<CurrentCctvStateResponse> states
) {
}
