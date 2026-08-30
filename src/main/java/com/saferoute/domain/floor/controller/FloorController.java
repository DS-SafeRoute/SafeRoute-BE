package com.saferoute.domain.floor.controller;

import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.request.UpdateFloorRequest;
import com.saferoute.domain.floor.dto.request.UploadFloorRequest;
import com.saferoute.domain.floor.dto.response.FloorImageUrlResponse;
import com.saferoute.domain.floor.dto.response.FloorResponse;
import com.saferoute.domain.floor.service.FloorService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.FloorSuccessCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "층/도면", description = "건물 내 층 및 도면 등록/조회/삭제 API")
@RestController
@RequestMapping("/api/v1/buildings/{buildingId}/floors")
@RequiredArgsConstructor
public class FloorController {

    private final FloorService floorService;

    // 건물별 도면 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FloorResponse>>> getFloors(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_LIST_FOUND,
                floorService.getFloors(buildingId, authentication.getName())));
    }

    // 층 등록
    @PostMapping
    public ResponseEntity<ApiResponse<FloorResponse>> createFloor(
            @PathVariable UUID buildingId,
            @Valid @ModelAttribute CreateFloorRequest request,
            Authentication authentication
    ) {
        FloorResponse response = floorService.createFloor(buildingId, request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(FloorSuccessCode.FLOOR_CREATED, response));
    }

    // 도면 등록
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FloorResponse>> uploadFloor(
        @PathVariable UUID buildingId,
        @Valid @ModelAttribute UploadFloorRequest request,
        Authentication authentication
    ) {
        FloorResponse response = floorService.uploadFloor(buildingId, request, authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(FloorSuccessCode.FLOOR_CREATED, response));
    }

    // 도면 상세 조회
    @GetMapping("/{floorId}")
    public ResponseEntity<ApiResponse<FloorResponse>> getFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_DETAIL_FOUND,
                floorService.getFloor(buildingId, floorId, authentication.getName())));
    }

    // 도면 이미지 조회용 Presigned GET URL 발급
    @GetMapping("/{floorId}/image-url")
    public ResponseEntity<ApiResponse<FloorImageUrlResponse>> getFloorImageUrl(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_IMAGE_URL_ISSUED,
                floorService.getFloorImageUrl(buildingId, floorId, authentication.getName())));
    }

    // 층 정보 수정
    @PatchMapping("/{floorId}")
    public ResponseEntity<ApiResponse<FloorResponse>> updateFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            @Valid @RequestBody UpdateFloorRequest request,
            Authentication authentication
    ) {
        FloorResponse response = floorService.updateFloor(
                buildingId, floorId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_UPDATED, response));
    }

    // 도면 삭제
    @DeleteMapping("/{floorId}")
    public ResponseEntity<ApiResponse<Void>> deleteFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            Authentication authentication
    ) {
        floorService.deleteFloor(buildingId, floorId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_DELETED, null));
    }

    // 도면 초기화 (이미지·노드·엣지만 삭제, 층은 유지)
    @DeleteMapping("/{floorId}/map")
    public ResponseEntity<ApiResponse<FloorResponse>> clearFloorMap(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId,
            Authentication authentication
    ) {
        FloorResponse response = floorService.clearFloorMap(buildingId, floorId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_MAP_CLEARED, response));
    }
}
