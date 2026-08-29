package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "훈련 세션의 이벤트 타임라인")
public record MonitoringEventListResponse(
        @Schema(description = "조회한 훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @ArraySchema(
                arraySchema = @Schema(description = "발생 시각 오름차순으로 정렬된 이벤트 목록. 없으면 빈 배열"),
                schema = @Schema(implementation = MonitoringEventResponse.class)
        )
        List<MonitoringEventResponse> events
) {
}
