package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;

import java.util.UUID;

public record CellResponse(
        UUID cellId,
        int rowIndex,
        int columnIndex
) {
    public static CellResponse from(FloorGridCell cell) {
        return new CellResponse(
                cell.getId(),
                cell.getRowIndex(),
                cell.getColumnIndex());
    }
}
