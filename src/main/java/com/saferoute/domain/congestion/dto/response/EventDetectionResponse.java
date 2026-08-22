package com.saferoute.domain.congestion.dto.response;

import com.saferoute.domain.congestion.entity.CongestionConfig;

public record EventDetectionResponse(
        Integer requiredConsecutiveFrames,
        Integer recoveryConsecutiveFrames,
        Integer cooldownSec
) {

    public static EventDetectionResponse from(CongestionConfig config) {
        return new EventDetectionResponse(
                config.getRequiredConsecutiveFrames(),
                config.getRecoveryConsecutiveFrames(),
                config.getCooldownSec()
        );
    }
}
