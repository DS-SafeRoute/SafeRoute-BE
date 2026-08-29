package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import com.saferoute.domain.evacuation.grid.entity.UserZone;

import java.util.List;

public record UserZoneCellsResponse(
        UserZoneResponse response,
        List<CellResponse> cells
        ) {
    public static UserZoneCellsResponse of(UserZone zone, int floorNum, List<FloorGridCell> cells){
        List<CellResponse> cellResponses = cells.stream()
                .map(CellResponse::from)
                .toList();
        return new UserZoneCellsResponse(
                UserZoneResponse.of(zone.getId(), zone.getName(), floorNum),
                cellResponses);
    }
}
