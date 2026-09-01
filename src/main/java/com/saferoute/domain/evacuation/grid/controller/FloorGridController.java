package com.saferoute.domain.evacuation.grid.controller;

import com.saferoute.domain.evacuation.grid.dto.request.CreateOrUpdateFloorGridRequest;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridCellPageResponse;
import com.saferoute.domain.evacuation.grid.dto.response.FloorGridResponse;
import com.saferoute.domain.evacuation.grid.service.FloorGridService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/floors/{floorId}/grid")
@RequiredArgsConstructor
@Validated
public class FloorGridController {

    private final FloorGridService floorGridService;

    @Operation(
            summary = "층 그리드 셀 목록 조회",
            description = """
                    지정한 층에 생성된 그리드 셀을 행(rowIndex) 오름차순, 같은 행에서는
                    열(columnIndex) 오름차순으로 페이지네이션 조회합니다. 셀 수가 수만 개 이상일
                    수 있어 전체를 한 번에 반환하지 않고 page/size로 나눠서 제공합니다.

                    각 셀은 walkable(보행 가능 여부), isFired(현재 화재 여부), 중심 정규화 좌표,
                    그리고 지정된 사용자 구역(UserZone)이 있으면 그 정보를 포함합니다. isFired는
                    훈련 중 화재 확산 시뮬레이션에 따라 동적으로 바뀌고 훈련 종료 시 초기화되므로,
                    실시간 상태가 필요하면 화면에서 주기적으로 다시 조회해야 합니다.

                    아직 그리드가 생성되지 않은 층을 조회하면 content가 빈 배열로 반환됩니다.
                    size는 최대 2000까지 허용됩니다.
                    """
    )
    @GetMapping("/cells")
    public ApiResponse<FloorGridCellPageResponse> getGridCells(
            @PathVariable UUID floorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int size,
            Authentication authentication
    ) {
        return ApiResponse.success(floorGridService.getGridCells(
                floorId, page, size, authentication.getName()));
    }

    @Operation(
            summary = "층 그리드 생성/재생성",
            description = """
                    지정한 층에 cellSizeMeter(미터 단위 셀 한 변 길이) 기준으로 격자 그리드를
                    새로 생성합니다. 이미 그리드가 존재하는 층에 다시 호출하면 최초 생성과 동일한
                    로직으로 전체를 재생성합니다(부분 수정 불가).

                    재생성 시 기존 그리드 셀은 모두 삭제되고(연결된 NodeGridCell/MapEdgeGridCell도
                    함께 삭제), 그 층의 사용자 지정 구역(UserZone)도 함께 삭제됩니다 - 셀 좌표
                    체계가 바뀌므로 구역 지정을 그대로 옮길 수 없기 때문입니다. CCTV 위치 노드도
                    삭제 후 재계산 대상이며(유도등 노드는 유지), 살아남은 노드/엣지는 새 그리드
                    좌표로 다시 매핑됩니다.

                    도면 세그멘테이션이 완료(DONE) 상태이고 실측 가로/세로 값(realWidth/
                    realHeight)이 설정되어 있어야 하며, 그렇지 않으면 오류가 발생합니다. 계산된
                    셀 개수가 너무 많거나(rows × columns 상한 초과) cellSizeMeter로 계산한
                    행/열 수가 0 이하이면(비정상적으로 큰 셀 크기) 오류가 발생합니다.
                    """
    )
    @PutMapping
    public ApiResponse<FloorGridResponse> createOrRegenerateGrid(
            @PathVariable UUID floorId,
            @Valid @RequestBody CreateOrUpdateFloorGridRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(floorGridService.createOrRegenerateGrid(
                floorId, request, authentication.getName()));
    }
}
