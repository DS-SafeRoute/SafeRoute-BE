package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.graph.dto.response.FloorGraphResponse;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "맵 그래프", description = "층별 맵 그래프(노드/엣지) 조회 API")
@RestController
@RequestMapping("/api/v1/floors/{floorId}")
@RequiredArgsConstructor
public class MapGraphController {

    private final MapGraphService mapGraphService;

    // 커스텀 편집 UI 캔버스 초기 로딩용 - 노드/엣지 전체 조회
    @Operation(
            summary = "층 맵 그래프 전체 조회",
            description = """
                    지정한 층에 등록된 모든 노드(STAIR/ROOM/HALLWAY/DOOR/EXIT/CUSTOM)와 엣지(통로)를
                    한 번에 반환합니다. 커스텀 편집 UI 캔버스를 처음 열 때 도면 위에 그래프를 그리는
                    용도로 사용됩니다.

                    노드에는 정규화 좌표(0.0~1.0, x/y), 타입, EXIT 대상 여부가, 엣지에는 연결된
                    fromNode/toNode, 실거리(distance), 양방향 여부(bidirectional)가 포함됩니다.
                    CUSTOM 타입 노드는 CCTV/IoT 유도등 등 기기 설치 위치를 나타내며 경로 목적지가
                    될 수 없습니다.

                    아직 노드나 엣지가 하나도 등록되지 않은 층이면 nodes/edges가 빈 배열로 반환됩니다.
                    노드/엣지의 개별 생성·수정·삭제는 이 API가 아니라 맵 그래프 편집 API를 사용합니다.
                    """
    )
    @GetMapping("/graph")
    public ResponseEntity<ApiResponse<FloorGraphResponse>> getGraph(
            @PathVariable UUID floorId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                mapGraphService.getFloorGraph(floorId, authentication.getName())));
    }
}
