package com.saferoute.domain.building.controller;

import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.service.BuildingService;
import com.saferoute.global.response.ApiResponse;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // 건물 등록
    @PostMapping
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request) {
        BuildingResponse response = buildingService.createBuilding(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("건물 등록에 성공했습니다.", response));
    }

    // 건물 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getBuildings() {
        return ResponseEntity.ok(ApiResponse.success(buildingService.getBuildings()));
    }

    // 건물 상세 조회
    @GetMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<BuildingResponse>> getBuilding(@PathVariable UUID buildingId) {
        return ResponseEntity.ok(ApiResponse.success(buildingService.getBuilding(buildingId)));
    }

    // 건물 정보 수정
    @PutMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<BuildingResponse>> updateBuilding(
            @PathVariable UUID buildingId,
            @Valid @RequestBody UpdateBuildingRequest request
    ) {
        BuildingResponse response = buildingService.updateBuilding(buildingId, request);
        return ResponseEntity.ok(ApiResponse.success("건물 정보 수정에 성공했습니다.", response));
    }

    // 건물 비활성화 (삭제 대신 비활성 처리)
    @PatchMapping("/{buildingId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateBuilding(@PathVariable UUID buildingId) {
        buildingService.deactivateBuilding(buildingId);
        return ResponseEntity.ok(ApiResponse.success("건물이 비활성화되었습니다.", null));
    }

    // 건물 삭제
    @DeleteMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<Void>> deleteBuilding(@PathVariable UUID buildingId) {
        buildingService.deleteBuilding(buildingId);
        return ResponseEntity.ok(ApiResponse.success("건물이 삭제되었습니다.", null));
    }
}