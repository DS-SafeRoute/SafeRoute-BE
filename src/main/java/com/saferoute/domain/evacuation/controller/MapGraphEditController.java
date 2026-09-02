package com.saferoute.domain.evacuation.controller;

import com.saferoute.domain.evacuation.graph.dto.request.CreateMapEdgeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapNodeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.dto.response.MapEdgeResponse;
import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.graph.service.MapGraphService;
import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.EvacuationSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "맵 그래프 편집", description = "맵 그래프 노드/엣지 생성/수정/삭제 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MapGraphEditController {

    private final MapGraphService mapGraphService;

    @Operation(
            summary = "맵 노드 생성",
            description = """
                    지정한 층에 새 노드(STAIR/ROOM/HALLWAY/DOOR/EXIT/START/CUSTOM)를 하나 추가합니다.
                    커스텀 편집 UI에서 캔버스에 노드를 드롭했을 때 호출합니다.

                    code는 같은 층 안에서 유일해야 하며(floor_id + code 유니크 제약), x/y는
                    도면 가로/세로 기준 0.0~1.0 정규화 좌표입니다. isExitTarget을 true로 생성하면
                    이 노드가 대피 경로 계산(다익스트라)의 목적지 후보에 포함됩니다.
                    단, START 노드는 요청값과 관계없이 isExitTarget=false로 저장됩니다.

                    CCTV/IoT 유도등 같은 기기 위치 노드는 이 API가 아니라 각 기기 도메인의
                    등록 API를 통해 CUSTOM 타입으로 생성됩니다.
                    """
    )
    @PostMapping("/floors/{floorId}/nodes")
    public ResponseEntity<ApiResponse<MapNodeResponse>> createNode(
            @PathVariable UUID floorId,
            @Valid @RequestBody CreateMapNodeRequest request
    ) {
        MapNodeResponse response = mapGraphService.createNode(floorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(EvacuationSuccessCode.MAP_NODE_CREATED, response));
    }

    @Operation(
            summary = "맵 노드 위치/유형/EXIT 대상 여부 수정",
            description = """
                    노드의 좌표(x, y), 선택적 유형(type), EXIT 대상(isExitTarget) 여부를 수정합니다.
                    커스텀 편집 UI에서 노드를 드래그로 이동하거나 EXIT 대상 지정을 토글할 때
                    사용합니다. type을 생략하면 기존 유형을 유지합니다.

                    한 층에는 START 노드를 하나만 지정할 수 있습니다. START는 항상
                    isExitTarget=false, EXIT는 항상 isExitTarget=true로 저장됩니다.

                    isExitTarget을 false로 바꾸는 요청은, 그 노드가 속한 층에 남은 EXIT 대상
                    노드가 이 노드 하나뿐이면 거부됩니다(마지막 출구는 해제할 수 없음). 이때
                    같은 층에 대한 동시 수정 요청은 직렬화되어 처리되므로, 두 관리자가 동시에
                    마지막 두 EXIT 노드를 각각 해제하려 해도 경쟁 상태 없이 하나만 성공합니다.
                    """
    )
    @PatchMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<MapNodeResponse>> updateNodePosition(
            @PathVariable UUID nodeId,
            @Valid @RequestBody UpdateMapNodePositionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(mapGraphService.updateNodePosition(nodeId, request)));
    }

    @Operation(
            summary = "맵 노드 삭제",
            description = """
                    노드 하나를 삭제합니다. 해당 노드에 연결된 엣지(통로)도 DB FK CASCADE로
                    함께 삭제되므로, 프론트는 삭제 후 그래프를 다시 조회해 연결이 끊어진 엣지가
                    사라졌는지 반영해야 합니다.

                    삭제 대상 노드가 EXIT 대상(isExitTarget=true)이고, 그 노드가 속한 층에 남은
                    EXIT 대상 노드가 하나뿐이면 삭제가 거부됩니다(마지막 출구 보호). 동일 층에
                    대한 동시 삭제 요청은 직렬화되어 처리됩니다.
                    """
    )
    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNode(@PathVariable UUID nodeId) {
        mapGraphService.deleteNode(nodeId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.MAP_NODE_DELETED, null));
    }

    @Operation(
            summary = "맵 엣지(통로) 생성",
            description = """
                    fromNodeId와 toNodeId 두 노드를 잇는 엣지(통로)를 생성합니다. 엣지가 속한
                    층은 별도 파라미터 없이 fromNode가 속한 floor를 그대로 사용하므로, 두 노드는
                    같은 층에 속해야 합니다.

                    distance는 실제 거리(미터)로 0보다 커야 하며 대피 경로 계산(다익스트라)의
                    기본 가중치로 쓰입니다. bidirectional이 true면 양방향 통행으로, false면
                    fromNode -> toNode 단방향으로만 경로 탐색 시 사용됩니다. room-hallway를
                    직접 연결하는 등 허용되지 않는 조합은 저장 계층에서 검증됩니다.
                    """
    )
    @PostMapping("/edges")
    public ResponseEntity<ApiResponse<MapEdgeResponse>> createEdge(@Valid @RequestBody CreateMapEdgeRequest request) {
        MapEdgeResponse response = mapGraphService.createEdge(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(EvacuationSuccessCode.MAP_EDGE_CREATED, response));
    }

    @Operation(
            summary = "맵 엣지(통로) 삭제",
            description = """
                    엣지(통로) 하나를 삭제합니다. 존재하지 않는 edgeId면 오류가 발생합니다.
                    삭제 후 해당 통로를 지나던 최단 경로 계산은 더 이상 이 엣지를 후보로
                    사용하지 않으므로, 프론트는 삭제 후 그래프를 다시 조회해 반영해야 합니다.
                    """
    )
    @DeleteMapping("/edges/{edgeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEdge(@PathVariable UUID edgeId) {
        mapGraphService.deleteEdge(edgeId);
        return ResponseEntity.ok(ApiResponse.success(EvacuationSuccessCode.MAP_EDGE_DELETED, null));
    }
}
