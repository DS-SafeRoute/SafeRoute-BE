package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import java.util.UUID;

public record CctvGridCellResponse(
        UUID id,
        int rowIndex,
        int columnIndex,
        boolean walkable,
        double centerX,
        double centerY
) {
    public static CctvGridCellResponse from(FloorGridCell cell) {
        return new CctvGridCellResponse(
                cell.getId(),
                cell.getRowIndex(),
                cell.getColumnIndex(),
                cell.isWalkable(),
                cell.getCenterX(),
                cell.getCenterY()
        );
    }
}
