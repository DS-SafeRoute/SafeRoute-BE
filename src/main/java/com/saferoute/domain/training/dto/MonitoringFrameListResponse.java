package com.saferoute.domain.training.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "카메라별 프레임 목록 (최신순 커서 페이지네이션)")
public record MonitoringFrameListResponse(
        @Schema(description = "조회한 훈련 세션 ID", example = "d669294e-55e1-4c00-bf67-229d89b76948")
        UUID sessionId,

        @Schema(description = "조회한 CCTV ID", example = "67b86e33-7874-494c-855f-e591e7847c09")
        UUID cctvId,

        @ArraySchema(
                arraySchema = @Schema(description = "최신 캡처순으로 정렬된 프레임 목록"),
                schema = @Schema(implementation = MonitoringFrameResponse.class)
        )
        List<MonitoringFrameResponse> frames,

        @Schema(
                description = "다음 페이지 조회에 사용할 커서. 다음 페이지가 없으면 null",
                example = "MTc4NzcyMjA5NTAwMA",
                nullable = true
        )
        String nextCursor,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext,

        @Schema(description = "이 세션+CCTV 조합의 전체 저장 프레임(Observation) 개수", example = "137")
        long totalCount
) {
}
