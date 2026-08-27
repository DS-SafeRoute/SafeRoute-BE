package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.entity.FireZone;
import java.time.Instant;
import java.util.UUID;

public record FireZoneResponse(
        UUID id,
        UUID scenarioId,
        UUID floorId,
        UUID gridCellId,
        boolean isManualAdd,
        int spreadGeneration,
        Instant addedAt
) {

    public static FireZoneResponse from(FireZone fireZone) {
        return new FireZoneResponse(
                fireZone.getId(),
                fireZone.getScenarioId(),
                fireZone.getFloorId(),
                fireZone.getGridCellId(),
                fireZone.getIsManualAdd(),
                fireZone.getSpreadGeneration(),
                fireZone.getAddedAt()
        );
    }
}
