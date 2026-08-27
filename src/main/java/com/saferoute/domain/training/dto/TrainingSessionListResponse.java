package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "조건에 맞는 훈련 세션 목록")
public record TrainingSessionListResponse(
        @ArraySchema(
                arraySchema = @Schema(description = "조회 조건에 맞는 세션 목록. 없으면 빈 배열"),
                schema = @Schema(implementation = TrainingSessionSummaryResponse.class)
        )
        List<TrainingSessionSummaryResponse> sessions
) {
}
