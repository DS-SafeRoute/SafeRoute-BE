package com.saferoute.domain.building.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.exception.BuildingNotFoundException;
import com.saferoute.domain.building.repository.BuildingRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;

    @Transactional
    public BuildingResponse createBuilding(CreateBuildingRequest request) {
        Building building = Building.create(
                request.name(),
                request.address(),
                request.totalFloors(),
                request.buildingType()
        );
        return BuildingResponse.from(buildingRepository.save(building));
    }

    public List<BuildingResponse> getBuildings() {
        return buildingRepository.findAll().stream()
                .map(BuildingResponse::from)
                .toList();
    }

    public BuildingResponse getBuilding(UUID buildingId) {
        return BuildingResponse.from(findBuildingById(buildingId));
    }

    @Transactional
    public BuildingResponse updateBuilding(UUID buildingId, UpdateBuildingRequest request) {
        Building building = findBuildingById(buildingId);
        building.update(request.name(), request.address(), request.totalFloors(), request.buildingType());
        return BuildingResponse.from(building);
    }

    @Transactional
    public void deactivateBuilding(UUID buildingId) {
        findBuildingById(buildingId).deactivate();
    }

    @Transactional
    public void deleteBuilding(UUID buildingId) {
        buildingRepository.delete(findBuildingById(buildingId));
    }

    private Building findBuildingById(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new BuildingNotFoundException(buildingId));
    }
}