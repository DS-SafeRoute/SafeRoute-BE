package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.service.EvacuationRoute;
import com.saferoute.domain.evacuation.service.EvacuationRouteService;
import com.saferoute.domain.evacuation.dto.response.EvacuationRouteResponse;
import com.saferoute.global.api.response.ApiResponse;
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
