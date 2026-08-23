package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import java.util.UUID;

public record CongestionEventReceivedData(
        String eventId,
        UUID edgeId,
        String cctvCode,
        CongestionEventType eventType,
        Double density,
        CongestionLevel congestionLevel,
        Long detectedAt,
        ImageUploadStatus imageUploadStatus
) {

    public static CongestionEventReceivedData from(UUID edgeId, CongestionEventItem item) {
        return new CongestionEventReceivedData(
                item.getEventId(),
                edgeId,
                item.getCctvCode(),
                item.getEventType(),
                item.getDensity(),
                item.getCongestionLevel(),
                item.getDetectedAt(),
                item.getImageUploadStatus()
        );
    }
}
