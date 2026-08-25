package com.saferoute.domain.floor.service;

import com.saferoute.domain.analysis.service.FloorAnalysisService;
import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.building.repository.BuildingRepository;
import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.request.UpdateFloorRequest;
import com.saferoute.domain.floor.dto.request.UploadFloorRequest;
import com.saferoute.domain.floor.dto.response.FloorResponse;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.entity.SegmentationStatus;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.AnalysisErrorCode;
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
    private final FloorAnalysisService floorAnalysisService;
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

        Floor floor = Floor.create(
                building,
                request.floorNum()
        );
        building.addFloor(request.floorNum());

        return FloorResponse.from(floorRepository.save(floor));
    }

    @Transactional
    public FloorResponse uploadFloor(UUID buildingId,UploadFloorRequest request){
        Building building = buildingRepository.findById(buildingId)
            .orElseThrow(() ->
                new ApiException(BuildingErrorCode.BUILDING_NOT_FOUND)
            );
        Floor floor = floorRepository.findByBuilding_IdAndFloorNum(building.getId(), request.floorNum())
            .orElseThrow(() ->
                new ApiException(FloorErrorCode.FLOOR_NOT_FOUND)
            );

        S3UploadResponse uploadResult =
            s3Service.upload(request.file());

        floor.upload(request.realHeight(), request.realWidth(), uploadResult.key());

        return FloorResponse.from(floor);
    }

    @Transactional
    public FloorResponse updateFloor(UUID buildingId, UUID floorId, UpdateFloorRequest request) {
        Floor floor = findFloor(buildingId, floorId);

        if (!floor.getFloorNum().equals(request.floorNum())) {
            validateDuplicateFloorNum(buildingId, request.floorNum());
            floor.getBuilding().removeFloor(floor.getFloorNum());
            floor.getBuilding().addFloor(request.floorNum());
        }

        floor.updateFloorNum(request.floorNum());

        return FloorResponse.from(floor);
    }

    @Transactional
    public void requestAnalysis(UUID floorId) {
        Floor floor = floorRepository.findById(floorId)
            .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));

        if (floor.getMapImageKey() == null) {
            throw new ApiException(FloorErrorCode.FLOOR_NOT_FOUND);
        }
        if (floor.getSegmentationStatus() == SegmentationStatus.PROCESSING) {
            throw new ApiException(AnalysisErrorCode.ANALYSIS_ALREADY_IN_PROGRESS);
        }

        floor.updateSegmentationStatus(SegmentationStatus.PROCESSING);
        floorAnalysisService.analyzeFloor(floorId);
    }


    @Transactional(readOnly = true)
    public FloorResponse getFloor(UUID buildingId, UUID floorId) {
        return FloorResponse.from(findFloor(buildingId, floorId));
    }

    @Transactional
    public void deleteFloor(UUID buildingId, UUID floorId) {
        Floor floor = findFloor(buildingId, floorId);
        floor.getBuilding().removeFloor(floor.getFloorNum());
        floorRepository.delete(floor);
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