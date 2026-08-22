package com.saferoute.domain.congestion.dto.response;

import com.saferoute.domain.congestion.entity.CongestionConfig;

public record CongestionConfigQueryResponse(
        boolean trainingActive,
        String trainingSessionId,
        String cctvCode,
        Double monitoredAreaM2,
        Long configVersion,
        Integer snapshotIntervalSec,
        Integer targetInferenceFps,
        CongestionThresholdsResponse congestionThresholds,
        EventDetectionResponse eventDetection
) {

    // 훈련 중이 아니면 Pi는 재시도 주기만 알면 되므로, 판정 설정은 내려주지 않고 configVersion만 포함한다.
    public static CongestionConfigQueryResponse inactive(String cctvCode, CongestionConfig config) {
        return new CongestionConfigQueryResponse(
                false, null, cctvCode, null, config.getVersion(), null, null, null, null
        );
    }

    public static CongestionConfigQueryResponse active(
            String trainingSessionId, String cctvCode, Double monitoredAreaM2, CongestionConfig config
    ) {
        return new CongestionConfigQueryResponse(
                true,
                trainingSessionId,
                cctvCode,
                monitoredAreaM2,
                config.getVersion(),
                config.getSnapshotIntervalSec(),
                config.getTargetInferenceFps(),
                CongestionThresholdsResponse.from(config),
                EventDetectionResponse.from(config)
        );
    }
}
