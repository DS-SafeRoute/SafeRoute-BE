package com.saferoute.domain.floor.controller;

import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.request.UploadFloorRequest;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "층/도면", description = "건물 내 층 및 도면 등록/조회/삭제 API")
@RestController
@RequestMapping("/api/v1/buildings/{buildingId}/floors")
@RequiredArgsConstructor
public class FloorController {

    private final FloorService floorService;

    // 건물별 도면 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FloorResponse>>> getFloors(@PathVariable UUID buildingId) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_LIST_FOUND, floorService.getFloors(buildingId)));
    }

    // 층 등록
    @PostMapping
    public ResponseEntity<ApiResponse<FloorResponse>> createFloor(
            @PathVariable UUID buildingId,
            @Valid @ModelAttribute CreateFloorRequest request
    ) {
        FloorResponse response = floorService.createFloor(buildingId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(FloorSuccessCode.FLOOR_CREATED, response));
    }

    // 도면 등록
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FloorResponse>> uploadFloor(
        @PathVariable UUID buildingId,
        @Valid @ModelAttribute UploadFloorRequest request
    ) {
        FloorResponse response = floorService.uploadFloor(buildingId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(FloorSuccessCode.FLOOR_CREATED, response));
    }

    // 도면 상세 조회
    @GetMapping("/{floorId}")
    public ResponseEntity<ApiResponse<FloorResponse>> getFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId
    ) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_DETAIL_FOUND, floorService.getFloor(buildingId, floorId)));
    }

    // 도면 삭제
    @DeleteMapping("/{floorId}")
    public ResponseEntity<ApiResponse<Void>> deleteFloor(
            @PathVariable UUID buildingId,
            @PathVariable UUID floorId
    ) {
        floorService.deleteFloor(buildingId, floorId);
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_DELETED, null));
    }
}