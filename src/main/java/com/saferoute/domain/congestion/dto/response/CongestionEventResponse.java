package com.saferoute.domain.congestion.dto.response;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.EventProcessingStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;

public record CongestionEventResponse(
        String eventId,
        String trainingSessionId,
        String cctvCode,
        CongestionEventType eventType,
        Long detectedAt,
        Integer headcount,
        Double localDensity,
        CongestionLevel localCongestionLevel,
        Double density,
        CongestionLevel congestionLevel,
        Long configVersion,
        String eventImageKey,
        EventProcessingStatus eventStatus,
        ImageUploadStatus imageUploadStatus
) {

    public static CongestionEventResponse from(CongestionEventItem item) {
        return new CongestionEventResponse(
                item.getEventId(),
                item.getTrainingSessionId(),
                item.getCctvCode(),
                item.getEventType(),
                item.getDetectedAt(),
                item.getHeadcount(),
                item.getLocalDensity(),
                item.getLocalCongestionLevel(),
                item.getDensity(),
                item.getCongestionLevel(),
                item.getConfigVersion(),
                item.getEventImageKey(),
                item.getEventStatus(),
                item.getImageUploadStatus()
        );
    }
}
