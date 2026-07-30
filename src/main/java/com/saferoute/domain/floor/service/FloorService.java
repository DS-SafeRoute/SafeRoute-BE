package com.saferoute.domain.floor.service;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.response.FloorResponse;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import java.util.List;
import java.util.UUID;

import com.saferoute.global.api.error.BuildingErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.S3UploadResponse;
import com.saferoute.infrastructure.s3.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FloorService {

    private final FloorRepository floorRepository;
    private final BuildingRepository buildingRepository;
    private final S3Service s3Service;

    public List<FloorResponse> getFloors(UUID buildingId) {
        validateBuildingExists(buildingId);
        return floorRepository.findByBuilding_IdOrderByFloorNumAsc(buildingId).stream()
                .map(FloorResponse::from)
                .toList();
    }

    @Transactional
    public FloorResponse createFloor(
            UUID buildingId,
            CreateFloorRequest request
    ) {
        Building building = buildingRepository.findById(buildingId)
                .orElseThrow(() ->
                        new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND)
                );

        validateDuplicateFloorNum(buildingId, request.floorNum());

        S3UploadResponse uploadResult =
                s3Service.upload(request.file());

        Floor floor = Floor.create(
                building,
                request.floorNum(),
                uploadResult.key()
        );

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
            throw new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND);
        }
    }

    private void validateDuplicateFloorNum(UUID buildingId, Integer floorNum) {
        if (floorRepository.existsByBuilding_IdAndFloorNum(buildingId, floorNum)) {
            throw new ApiException(FloorErrorCode.DUPLICATE_FLOOR_NUM);
        }
    }

    private Floor findFloor(UUID buildingId, UUID floorId) {
        return floorRepository.findByIdAndBuilding_Id(floorId, buildingId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
    }
}