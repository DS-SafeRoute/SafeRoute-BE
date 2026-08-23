package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import java.util.UUID;

public record CongestionEventImageUpdatedData(
        UUID eventId,
        String eventImageKey,
        Long uploadedAt,
        ImageUploadStatus imageUploadStatus
) {
    public static CongestionEventImageUpdatedData from(CongestionEventItem item) {
        return new CongestionEventImageUpdatedData(
                UUID.fromString(item.getEventId()),
                item.getEventImageKey(),
                item.getImageUploadedAt(),
                item.getImageUploadStatus()
        );
    }
}
