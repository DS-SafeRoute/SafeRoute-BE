package com.saferoute.domain.building.controller;

import com.saferoute.domain.building.dto.response.BuildingResponse;
import com.saferoute.domain.building.dto.request.CreateBuildingRequest;
import com.saferoute.domain.building.dto.request.UpdateBuildingRequest;
import com.saferoute.domain.building.service.BuildingService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.BuildingSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(
            summary = "건물 등록",
            description = """
                    요청자가 속한 학교(schoolName) 소속으로 새 건물을 등록합니다. schoolName은
                    요청자의 인증 정보로부터 서버가 직접 결정하며 요청 바디로 받지 않습니다.

                    groundFloorCount/basementFloorCount/totalFloors는 이 API로 지정할 수 없고
                    항상 0으로 시작하며, 이후 층 등록/삭제 API를 호출할 때마다 자동으로
                    증감합니다. isActive는 생성 시 true로 설정됩니다.
                    """
    )
    @PostMapping
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(
            @Valid @RequestBody CreateBuildingRequest request,
            Authentication authentication) {
        BuildingResponse response = buildingService.createBuilding(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(BuildingSuccessCode.BUILDING_CREATED, response));
    }

    // 건물 목록 조회
    @Operation(
            summary = "건물 목록 조회",
            description = """
                    요청자가 속한 학교의 모든 건물을 등록일(createdAt) 최신순으로 반환합니다.

                    isActive가 false로 비활성화된 건물도 목록에서 제외되지 않고 그대로
                    포함되므로, 비활성 건물을 화면에서 숨기거나 다르게 표시하려면 프론트에서
                    응답의 isActive 값을 보고 직접 필터링해야 합니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<BuildingResponse>>> getBuildings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_LIST_FOUND,
                buildingService.getBuildings(authentication.getName())));
    }

    // 건물 상세 조회
    @Operation(
            summary = "건물 상세 조회",
            description = """
                    buildingId로 건물 단건을 조회합니다. 요청자와 같은 학교 소속 건물만 조회할
                    수 있으며, 존재하지 않거나 다른 학교 소속 건물이면 404가 반환됩니다.

                    groundFloorCount/basementFloorCount/totalFloors는 층 등록/삭제에 따라
                    서버가 자동 계산한 값이며, isActive가 false여도 상세 조회 자체는 계속
                    가능합니다.
                    """
    )
    @GetMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<BuildingResponse>> getBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DETAIL_FOUND,
                buildingService.getBuilding(buildingId, authentication.getName())));
    }

    // 건물 정보 수정
    @Operation(
            summary = "건물 정보 수정",
            description = """
                    건물의 name/address/buildingType을 수정합니다. 요청 바디에 없는 필드는
                    부분 수정(PATCH)이 아니라 항상 세 값을 모두 함께 덮어쓰는 방식이므로,
                    프론트는 기존 값을 채운 뒤 변경분만 반영해 전체 필드를 보내야 합니다.

                    groundFloorCount/basementFloorCount/totalFloors, isActive는 이 API로
                    수정할 수 없습니다(층수는 층 등록/삭제로만, isActive는 비활성화 API로만
                    변경됩니다).
                    """
    )
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
    @Operation(
            summary = "건물 비활성화",
            description = """
                    건물을 삭제하지 않고 isActive만 false로 바꾸는 소프트 삭제 API입니다.
                    건물에 속한 층, 훈련 이력 등 다른 데이터는 그대로 남아 있으며, 이후에도
                    건물 상세/목록 조회에서는 계속 노출됩니다(목록 API가 isActive로 걸러주지
                    않으므로 화면에서 숨기려면 프론트에서 필터링이 필요합니다).

                    훈련 기록이 있어 건물 삭제 API가 실패하는 건물은 이 API로 비활성 처리하는
                    것을 대신 사용하면 됩니다. 다만 현재 이 API를 다시 활성화(isActive=true로
                    되돌리는) 엔드포인트는 제공되지 않습니다.
                    """
    )
    @PatchMapping("/{buildingId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        buildingService.deactivateBuilding(buildingId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DEACTIVATED, null));
    }

    // 건물 삭제
    @Operation(
            summary = "건물 삭제",
            description = """
                    건물을 DB에서 완전히 삭제합니다. TrainingScenario.building 관계에는 삭제
                    전파(CASCADE)가 설정되어 있지 않으므로, 이 건물을 참조하는 훈련 기록이
                    하나라도 있으면 FK 제약 위반으로 삭제가 거부되고 실패 응답이 반환됩니다.
                    이 경우에는 완전 삭제 대신 건물 비활성화 API를 사용해야 합니다.

                    요청자와 같은 학교 소속 건물만 삭제할 수 있으며, 존재하지 않거나 다른
                    학교 소속이면 404가 반환됩니다.
                    """
    )
    @DeleteMapping("/{buildingId}")
    public ResponseEntity<ApiResponse<Void>> deleteBuilding(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        buildingService.deleteBuilding(buildingId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(BuildingSuccessCode.BUILDING_DELETED, null));
    }
}
