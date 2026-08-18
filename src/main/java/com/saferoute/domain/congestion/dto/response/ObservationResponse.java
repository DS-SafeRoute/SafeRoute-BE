package com.saferoute.domain.congestion.dto.response;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;

public record ObservationResponse(
        String eventId,
        String trainingSessionId,
        String cctvCode,
        Double avgHeadcount,
        Integer peakHeadcount,
        Integer sampleCount,
        Double density,
        CongestionLevel congestionLevel,
        Long windowStart,
        Long windowEnd,
        Long capturedAt,
        String monitoringImageKey,
        Long configVersion,
        Long expiresAt
) {

    public static ObservationResponse from(ObservationItem item) {
        return new ObservationResponse(
                item.getEventId(),
                item.getTrainingSessionId(),
                item.getCctvCode(),
                item.getAvgHeadcount(),
                item.getPeakHeadcount(),
                item.getSampleCount(),
                item.getDensity(),
                item.getCongestionLevel(),
                item.getWindowStart(),
                item.getWindowEnd(),
                item.getCapturedAt(),
                item.getMonitoringImageKey(),
                item.getConfigVersion(),
                item.getExpiresAt()
        );
    }
}
