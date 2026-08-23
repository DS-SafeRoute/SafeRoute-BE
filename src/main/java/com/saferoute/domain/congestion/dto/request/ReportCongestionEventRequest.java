package com.saferoute.domain.congestion.dto.request;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

// Pi가 혼잡 진입/상승/종료를 감지한 즉시 보내는 이벤트. edgeId는 Pi가 보내지 않는다 - BE가
// CCTV -> GridCell -> MapEdge 매핑으로 직접 찾는다. localDensity/localCongestionLevel은 Pi의
// 참고값일 뿐이며, 최종 density/congestionLevel은 BE가 다시 계산한다.
public record ReportCongestionEventRequest(
        @NotNull UUID eventId,
        @NotNull UUID trainingSessionId,
        @NotBlank String cctvCode,
        @NotNull CongestionEventType eventType,
        @NotNull @Positive Long detectedAt,
        @NotNull @PositiveOrZero Integer headcount,
        @NotNull @PositiveOrZero Double localDensity,
        @NotNull CongestionLevel localCongestionLevel,
        @NotNull @Positive Long configVersion
) {
}
