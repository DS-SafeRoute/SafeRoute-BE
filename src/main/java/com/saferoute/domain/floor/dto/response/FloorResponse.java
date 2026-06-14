package com.saferoute.domain.floor.dto.response;

import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import java.time.Instant;
import java.util.UUID;

public record FloorResponse(
        UUID id,
        Integer floorNum,
        String mapImageUrl,
        SegmentationStatus segmentationStatus,
        Instant processedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static FloorResponse from(Floor floor) {
        return new FloorResponse(
                floor.getId(),
                floor.getFloorNum(),
                floor.getMapImageUrl(),
                floor.getSegmentationStatus(),
                floor.getProcessedAt(),
                floor.getCreatedAt(),
                floor.getUpdatedAt()
        );
    }
}