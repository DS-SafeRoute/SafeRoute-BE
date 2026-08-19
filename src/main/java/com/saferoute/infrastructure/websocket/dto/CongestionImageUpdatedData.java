package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.UUID;

public record CongestionImageUpdatedData(
        UUID eventId,
        String eventImageKey,
        Long uploadedAt,
        ImageUploadStatus imageUploadStatus
) {
    public static CongestionImageUpdatedData from(ObservationItem item) {
        return new CongestionImageUpdatedData(
                UUID.fromString(item.getEventId()),
                item.getEventImageKey(),
                item.getImageUploadedAt(),
                item.getImageUploadStatus()
        );
    }
}
