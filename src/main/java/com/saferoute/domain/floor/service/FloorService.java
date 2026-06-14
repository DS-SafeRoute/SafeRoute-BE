package com.saferoute.domain.floor.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.exception.BuildingNotFoundException;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.response.FloorResponse;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.exception.FloorNotFoundException;
import com.saferoute.domain.floor.repository.FloorRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;

    public List<FloorResponse> getFloors(UUID buildingId) {
        validateBuildingExists(buildingId);
        return floorRepository.findByBuilding_IdOrderByFloorNumAsc(buildingId).stream()
                .map(FloorResponse::from)
                .toList();
    }

    @Transactional
    public FloorResponse createFloor(UUID buildingId, CreateFloorRequest request) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BuildingNotFoundException(buildingId));

        Floor floor = Floor.create(building, request.floorNum(), request.mapImageUrl());
        return FloorResponse.from(floorRepository.save(floor));
    }

    public FloorResponse getFloor(UUID buildingId, UUID floorId) {
        return FloorResponse.from(findFloor(buildingId, floorId));
    }

    @Transactional
    public void deleteFloor(UUID buildingId, UUID floorId) {
        floorRepository.delete(findFloor(buildingId, floorId));
    }

    private void validateBuildingExists(UUID buildingId) {
        if (!buildingRepository.existsById(buildingId)) {
            throw new BuildingNotFoundException(buildingId);
        }
    }

    private Floor findFloor(UUID buildingId, UUID floorId) {
        return floorRepository.findByIdAndBuilding_Id(floorId, buildingId)
                .orElseThrow(() -> new FloorNotFoundException(floorId));
    }
}
