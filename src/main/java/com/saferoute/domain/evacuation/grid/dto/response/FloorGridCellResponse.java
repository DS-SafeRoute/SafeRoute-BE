package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import java.util.UUID;

public record FloorGridCellResponse(
        UUID id,
        int rowIndex,
        int columnIndex,
        boolean walkable,
        boolean fired,
        double centerX,
        double centerY
) {
    public static FloorGridCellResponse from(FloorGridCell cell) {
        return new FloorGridCellResponse(
                cell.getId(),
                cell.getRowIndex(),
                cell.getColumnIndex(),
                cell.isWalkable(),
                cell.isFired(),
                cell.getCenterX(),
                cell.getCenterY()
        );
    }
}
