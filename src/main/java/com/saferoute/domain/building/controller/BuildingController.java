package com.saferoute.domain.building.controller;

import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.service.BuildingService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.BuildingSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "건물", description = "건물 등록/조회/수정/삭제 API")
@RestController
@RequestMapping("/api/v1/buildings")
@RequiredArgsConstructor
public class BuildingController {

    private final BuildingService buildingService;

    // 건물 등록
    @PostMapping
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request,
            Authentication authentication) {
        BuildingResponse response = buildingService.createBuilding(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BuildingSuccessCode.BUILDING_CREATED, response));
    }

    // 건물 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getBuildings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_LIST_FOUND,
                buildingService.getBuildings(authentication.getName())));
    }

    // 건물 상세 조회
    @GetMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<BuildingResponse>> getBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DETAIL_FOUND,
                buildingService.getBuilding(buildingId, authentication.getName())));
    }

    // 건물 정보 수정
    @PutMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<BuildingResponse>> updateBuilding(
            @PathVariable UUID buildingId,
            @Valid @RequestBody UpdateBuildingRequest request,
            Authentication authentication
    ) {
        BuildingResponse response = buildingService.updateBuilding(buildingId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_UPDATED, response));
    }

    // 건물 비활성화 (삭제 대신 비활성 처리)
    @PatchMapping("/{buildingId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        buildingService.deactivateBuilding(buildingId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DEACTIVATED, null));
    }

    // 건물 삭제
    @DeleteMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<Void>> deleteBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        buildingService.deleteBuilding(buildingId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DELETED, null));
    }
}
