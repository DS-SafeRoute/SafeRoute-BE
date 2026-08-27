package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.training.entity.FireZone;
import java.util.List;
import java.util.UUID;

public record FireSpreadEventData(
        int spreadGeneration,
        List<Cell> newlyFiredCells
) {

    public static FireSpreadEventData from(int spreadGeneration, List<FireZone> newlyFired) {
        return new FireSpreadEventData(
                spreadGeneration,
                newlyFired.stream()
                        .map(fz -> new Cell(fz.getGridCellId(), fz.getFloorId()))
                        .toList()
        );
    }

    public record Cell(UUID gridCellId, UUID floorId) {
    }
}
