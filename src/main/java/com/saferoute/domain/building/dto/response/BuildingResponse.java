package com.saferoute.domain.building.dto.response;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.entity.BuildingType;
import java.time.Instant;
import java.util.UUID;

public record BuildingResponse(
        UUID id,
        String name,
        String address,
        String schoolName,
        Integer groundFloorCount,
        Integer basementFloorCount,
        Integer totalFloors,
        BuildingType buildingType,
        Boolean isActive,
        Instant lastTrainedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static BuildingResponse from(Building building) {
        return new BuildingResponse(
                building.getId(),
                building.getName(),
                building.getAddress(),
                building.getSchoolName(),
                building.getGroundFloorCount(),
                building.getBasementFloorCount(),
                building.getTotalFloors(),
                building.getBuildingType(),
                building.getIsActive(),
                building.getLastTrainedAt(),
                building.getCreatedAt(),
                building.getUpdatedAt()
        );
    }
}
