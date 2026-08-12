package com.saferoute.domain.device.dto.response;

import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import java.util.List;
import java.util.UUID;

public record CctvResponse(
        UUID id,
        String code,
        String name,
        UUID floorId,
        UUID customNodeId,
        double x,
        double y,
        boolean enabled,
        Double gridCellSizeMeter,
        int monitoredGridCellCount,
        Double monitoredAreaM2,
        List<CctvGridCellResponse> gridCells
) {
    public static CctvResponse of(Cctv cctv, List<FloorGridCell> gridCells) {
        Double cellSizeMeter = cctv.getCustomNode().getFloor().getGridCellSizeMeter();
        boolean validCellSizeMeter = cellSizeMeter != null && cellSizeMeter > 0;
        Double monitoredAreaM2 = !validCellSizeMeter
                ? null
                : gridCells.size() * cellSizeMeter * cellSizeMeter;
        return new CctvResponse(
                cctv.getId(),
                cctv.getCode(),
                cctv.getName(),
                cctv.getCustomNode().getFloor().getId(),
                cctv.getCustomNode().getId(),
                cctv.getCustomNode().getX(),
                cctv.getCustomNode().getY(),
                cctv.isEnabled(),
                cellSizeMeter,
                gridCells.size(),
                monitoredAreaM2,
                gridCells.stream().map(CctvGridCellResponse::from).toList()
        );
    }
}
