package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.evacuation.dto.response.EvacuationRouteResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /graph(노드/엣지 편집용) 엔드포인트와 역할 분리를 위해 별도 컨트롤러로 구성 - MapGraphController 참고
@Tag(name = "대피 경로", description = "최단 대피 경로 조회 API")
@RestController
@RequestMapping("/api/v1/floors/{floorId}")
@RequiredArgsConstructor
public class EvacuationRouteController {

    private final EvacuationRouteService evacuationRouteService;

    // 시작 노드에서 가장 가까운 EXIT까지의 추천 경로 (mock data 기준, congestion/danger 0 고정)
    @Operation(
            summary = "최단 대피 경로 조회",
            description = """
                    지정한 층에서 startNodeId부터 가장 가까운 EXIT 대상 노드까지 다익스트라로 계산한
                    최단 경로를 노드 목록(출발 -> 도착 순서)과 총 가중치(totalWeight)로 반환합니다.

                    가중치는 기본적으로 엣지의 실거리(distance) 합이며, 혼잡도/위험도 가중치는
                    아직 DynamoDB 혼잡도·화재 확산 데이터 연동 전이라 항상 0으로 고정되어 있어
                    현재는 사실상 최단 거리 경로와 동일합니다. 훈련 중 혼잡으로 트리거된 우회 경로는
                    이 API가 아니라 재탐색 승인 API(RouteRecalculation)를 통해 반영됩니다.

                    startNodeId는 대상 층에 실제로 존재하는 노드여야 하며, 해당 층에 EXIT 대상으로
                    지정된 노드가 하나도 없거나 그래프상 도달 가능한 경로가 없으면 오류가 발생합니다.
                    """
    )
    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<EvacuationRouteResponse>> getShortestRoute(
            @PathVariable UUID floorId,
            @RequestParam UUID startNodeId,
            Authentication authentication
    ) {
        EvacuationRoute route = evacuationRouteService.findShortestRoute(
                floorId, startNodeId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(EvacuationRouteResponse.from(route)));
    }
}
