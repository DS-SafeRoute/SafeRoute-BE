package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;

import java.util.List;
import java.util.UUID;

public record UserZoneResponse(
        UUID UserZoneId,
        String UserZoneName,
        int FloorNum,
        List<FloorGridCell> cells

) {
    public static UserZoneResponse of(UUID userZoneId, String userZoneName, int floorNum, List<FloorGridCell> cells) {
        return new UserZoneResponse(
                userZoneId,
                userZoneName,
                floorNum,
                cells
        );
    }
}
