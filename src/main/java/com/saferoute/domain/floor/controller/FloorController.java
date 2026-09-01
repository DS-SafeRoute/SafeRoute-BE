package com.saferoute.domain.floor.controller;

import com.saferoute.domain.floor.dto.request.CreateFloorRequest;
import com.saferoute.domain.floor.dto.request.UpdateFloorRequest;
import com.saferoute.domain.floor.dto.request.UploadFloorRequest;
import com.saferoute.domain.floor.dto.response.FloorImageUrlResponse;
import com.saferoute.domain.floor.dto.response.FloorResponse;
import com.saferoute.domain.floor.service.FloorService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.FloorSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(
            summary = "건물별 도면 목록 조회",
            description = """
                    지정한 건물(buildingId)에 등록된 모든 층(Floor)을 floorNum 오름차순으로
                    반환합니다. 지하층은 floorNum이 음수이므로 지하층 → 지상층 순으로 정렬됩니다.

                    아직 도면 이미지를 업로드하지 않은 층(mapImageKey가 null, segmentationStatus가
                    PENDING)도 목록에 함께 포함됩니다. 응답의 mapImageKey는 S3 원본 키일 뿐 바로
                    렌더링 가능한 URL이 아니므로, 이미지를 화면에 표시하려면 각 층에 대해
                    도면 이미지 조회용 Presigned URL 발급 API를 별도로 호출해야 합니다.

                    buildingId가 존재하지 않거나 요청자와 다른 학교 소속 건물이면 404가
                    반환됩니다.
                    """
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<FloorResponse>>> getFloors(
            @PathVariable UUID buildingId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(FloorSuccessCode.FLOOR_LIST_FOUND,
                floorService.getFloors(buildingId, authentication.getName())));
    }

    // 층 등록
    @Operation(
            summary = "층 등록 (도면 이미지 없이)",
            description = """
                    도면 이미지 없이 층 번호(floorNum)만으로 층을 먼저 등록합니다. 생성된 층은
                    mapImageKey가 null이고 segmentationStatus는 PENDING 상태이며, 실제 도면
                    이미지는 이후 도면 등록(업로드) API로 같은 floorNum에 대해 별도로 올려야 합니다.

                    같은 건물 안에서 floorNum은 중복될 수 없습니다(uk_floor_building_floornum).
                    이미 존재하는 floorNum으로 요청하면 실패합니다.

                    floorNum이 음수이면 지하층, 0 이상이면 지상층으로 취급되며, 등록에 성공하면
                    건물(Building)의 groundFloorCount/basementFloorCount/totalFloors가 자동으로
                    1씩 증가합니다. 이 값들은 클라이언트가 직접 수정할 수 없고 오직 층 등록/삭제로만
                    변경됩니다.
                    """
    )
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
    @Operation(
            summary = "도면 이미지 업로드",
            description = """
                    이미 등록되어 있는 층에 도면 이미지 파일과 실제 크기(realWidth, realHeight,
                    미터 단위)를 업로드합니다. 대상 층은 floorId가 아니라 floorNum으로 지정하며,
                    해당 buildingId 안에 그 floorNum을 가진 층이 없으면 실패하므로 반드시 층 등록
                    API로 층을 먼저 만든 뒤 호출해야 합니다.

                    업로드에 성공하면 mapImageKey/realWidth/realHeight가 채워지고
                    segmentationStatus가 DONE으로 바뀝니다. 이미 이미지가 있는 층에 다시
                    호출하면 기존 값을 덮어쓰며, 이전 S3 객체는 별도로 삭제되지 않습니다.

                    realWidth/realHeight는 도면 픽셀 좌표를 실제 거리로 환산하는 데 쓰이므로
                    반드시 양수여야 합니다.
                    """
    )
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
    @Operation(
            summary = "도면(층) 상세 조회",
            description = """
                    buildingId에 속한 특정 floorId의 층 정보를 단건 조회합니다. 층이 없거나
                    지정한 buildingId 소속이 아니면(다른 건물/다른 학교) 404가 반환됩니다.

                    응답의 mapImageKey는 S3 원본 키이며 바로 렌더링 가능한 URL이 아닙니다.
                    이미지를 화면에 표시하려면 도면 이미지 조회용 Presigned URL 발급 API를
                    추가로 호출해야 합니다.
                    """
    )
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
    @Operation(
            summary = "도면 이미지 조회용 Presigned URL 발급",
            description = """
                    해당 층의 도면 이미지를 화면에 렌더링할 수 있도록 만료 시간이 있는 S3
                    presigned GET URL(imageUrl)과 만료 시각(expiresAt)을 발급합니다.

                    아직 도면 이미지를 업로드하지 않은 층(mapImageKey가 null)에 대해 호출하면
                    404가 반환되므로, 프론트는 층 조회 응답의 mapImageKey가 null이 아닐 때만
                    이 API를 호출해야 합니다.

                    imageUrl은 영구 URL이 아니라 expiresAt 시점에 만료되므로, 화면에 값을 오래
                    캐싱하지 말고 만료 시점이 가까워지면 이 API를 다시 호출해 새 URL을 받아야
                    합니다.
                    """
    )
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
    @Operation(
            summary = "층 번호 수정",
            description = """
                    지정한 floorId의 floorNum만 수정합니다. 도면 이미지·실제 크기 등은 이 API로
                    변경할 수 없으며, 이미지를 새로 반영하려면 도면 등록(업로드) API를 다시
                    호출해야 합니다.

                    새 floorNum이 기존 값과 다르면 같은 건물 안에서 중복 여부를 다시 검사하며,
                    이미 다른 층이 그 floorNum을 쓰고 있으면 실패합니다. 검사를 통과하면
                    건물(Building)의 groundFloorCount/basementFloorCount/totalFloors가
                    기존 floorNum 기준으로 1 감소, 새 floorNum 기준으로 1 증가하도록 재계산됩니다.
                    즉 지상층(0 이상)과 지하층(음수) 사이를 오가도록 floorNum을 바꾸면 지상/지하
                    층수 구성이 함께 바뀝니다.
                    """
    )
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
    @Operation(
            summary = "도면(층) 삭제",
            description = """
                    해당 층(Floor row) 자체를 완전히 삭제합니다. 이미지만 지우고 층은 남기는
                    도면 초기화 API와 달리, 이 API를 호출하면 층이 목록에서 완전히 사라집니다.

                    DB의 ON DELETE CASCADE 설정으로 인해 이 층에 속한 대피 경로 그래프의
                    노드(MapNode)와 엣지(MapEdge)도 함께 삭제됩니다. 되돌릴 수 없는 작업이므로
                    프론트에서는 삭제 전 사용자 확인을 받는 것을 권장합니다.

                    삭제에 성공하면 건물(Building)의 groundFloorCount/basementFloorCount/
                    totalFloors가 해당 층의 floorNum 기준으로 1씩 감소합니다.
                    """
    )
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
    @Operation(
            summary = "도면 초기화 (이미지만 삭제, 층은 유지)",
            description = """
                    층(Floor) row 자체는 삭제하지 않고, 도면 이미지(mapImageKey)·실제 크기
                    (realWidth/realHeight)·그리드 정보·처리 시각(processedAt)만 초기화하고
                    segmentationStatus를 PENDING으로 되돌립니다. floorNum과 floorId는 그대로
                    유지되므로 층 목록에서는 사라지지 않습니다.

                    이 층에 속한 대피 경로 그래프의 노드(MapNode)와 엣지(MapEdge)도 함께
                    삭제되어 도면과 관련된 경로 데이터가 모두 초기화됩니다.

                    segmentationStatus가 PROCESSING(AI 세그멘테이션 진행 중)인 층에는 호출할 수
                    없으며 409로 실패하므로, 프론트는 분석이 끝난 뒤 다시 시도해야 합니다.
                    초기화 후 같은 floorNum으로 도면 등록(업로드) API를 다시 호출해 새 이미지를
                    올릴 수 있습니다.
                    """
    )
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
