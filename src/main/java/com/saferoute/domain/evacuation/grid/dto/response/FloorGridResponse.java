package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.floor.entity.Floor;
import java.util.UUID;

public record FloorGridResponse(
        UUID floorId,
        int rows,
        int columns,
        double cellSizeMeter
) {
    public static FloorGridResponse of(Floor floor) {
        return new FloorGridResponse(
                floor.getId(), floor.getGridRows(), floor.getGridColumns(),
                floor.getGridCellSizeMeter()
        );
    }
}