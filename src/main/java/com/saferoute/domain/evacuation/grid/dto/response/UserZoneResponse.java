package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;

import java.util.List;
import java.util.UUID;

public record UserZoneResponse(
        UUID userZoneId,
        String userZoneName,
        int floorNum

) {
    public static UserZoneResponse of(UUID userZoneId, String userZoneName, int floorNum) {
        return new UserZoneResponse(
                userZoneId,
                userZoneName,
                floorNum
        );
    }
}
