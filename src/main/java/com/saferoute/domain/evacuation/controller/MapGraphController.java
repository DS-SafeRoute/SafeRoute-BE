package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.graph.dto.response.FloorGraphResponse;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.global.api.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @GetMapping("/graph")
    public ResponseEntity<ApiResponse<FloorGraphResponse>> getGraph(@PathVariable UUID floorId) {
        return ResponseEntity.ok(ApiResponse.success(mapGraphService.getFloorGraph(floorId)));
    }
}
