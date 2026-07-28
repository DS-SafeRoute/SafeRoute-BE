package com.saferoute.domain.building.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.service.FloorCleanupService;
import java.util.List;
import java.util.UUID;

import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final FloorCleanupService floorCleanupService;

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
        Building building = findBuildingById(buildingId);
        // Floor -> MapNode/MapEdge/Cctv/IoTLight/FireZone/FloorGridCell 는 cascade 매핑이 없으므로
        // Building 삭제 시 JPA가 Floor는 cascade로 지워도 그 하위 자식은 알지 못해 FK 위반이 난다.
        // 따라서 각 층의 자식들을 먼저 정리한 뒤 building을 삭제한다. (Floor 자체는 Building의
        // cascade=ALL, orphanRemoval=true로 정상 삭제됨)
        for (Floor floor : building.getFloors()) {
            floorCleanupService.cleanupFloorChildren(floor.getId());
        }
        buildingRepository.delete(building);
    }

    private Building findBuildingById(UUID buildingId) {
        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND));
    }
}