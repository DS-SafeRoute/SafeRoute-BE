package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "훈련 세션의 모니터링 카메라 목록")
public record MonitoringCameraListResponse(
        @Schema(description = "조회한 훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @ArraySchema(
                arraySchema = @Schema(description = "활성 CCTV 카드 목록. CCTV가 없으면 빈 배열"),
                schema = @Schema(implementation = MonitoringCameraResponse.class)
        )
        List<MonitoringCameraResponse> cameras
) {
}
