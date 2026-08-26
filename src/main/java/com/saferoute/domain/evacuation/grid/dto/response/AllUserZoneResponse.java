package com.saferoute.domain.evacuation.grid.dto.response;

import com.saferoute.domain.evacuation.grid.entity.UserZone;

import java.util.List;

public record AllUserZoneResponse(
        List<UserZoneResponse> userzones
) {
    public static AllUserZoneResponse of(List<UserZone> userzones, int floorNum){
        List<UserZoneResponse> responses = userzones.stream()
                .map(zone -> UserZoneResponse.of(
                        zone.getId(),
                        zone.getName(),
                        floorNum
                ))
                .toList();
        return new AllUserZoneResponse(responses);
    }
}
