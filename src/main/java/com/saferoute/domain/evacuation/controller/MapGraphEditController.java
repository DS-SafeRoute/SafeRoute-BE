package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.graph.dto.request.CreateMapEdgeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapNodeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.dto.response.MapEdgeResponse;
import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.EvacuationSuccessCode;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 커스텀 편집 UI(드래그로 노드 추가/이동, 엣지 연결)를 위한 CRUD 엔드포인트
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MapGraphEditController {

    private final MapGraphService mapGraphService;

    @PostMapping("/floors/{floorId}/nodes")
    public ResponseEntity<ApiResponse<MapNodeResponse>> createNode(
            @PathVariable UUID floorId,
            @Valid @RequestBody CreateMapNodeRequest request
    ) {
        MapNodeResponse response = mapGraphService.createNode(floorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(EvacuationSuccessCode.MAP_NODE_CREATED, response));
    }

    @PatchMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<MapNodeResponse>> updateNodePosition(
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateMapNodePositionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(mapGraphService.updateNodePosition(nodeId, request)));
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNode(@PathVariable UUID nodeId) {
        mapGraphService.deleteNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.MAP_NODE_DELETED, null));
    }

    @PostMapping("/edges")
    public ResponseEntity<ApiResponse<MapEdgeResponse>> createEdge(@Valid @RequestBody CreateMapEdgeRequest request) {
        MapEdgeResponse response = mapGraphService.createEdge(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(EvacuationSuccessCode.MAP_EDGE_CREATED, response));
    }

    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEdge(@PathVariable UUID edgeId) {
        mapGraphService.deleteEdge(edgeId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.MAP_EDGE_DELETED, null));
    }
}
