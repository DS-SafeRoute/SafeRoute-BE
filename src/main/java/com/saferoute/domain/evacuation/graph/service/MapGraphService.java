package com.saferoute.domain.evacuation.graph.service;

import com.saferoute.domain.evacuation.graph.dto.request.CreateMapEdgeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.CreateMapNodeRequest;
import com.saferoute.domain.evacuation.graph.dto.request.UpdateMapNodePositionRequest;
import com.saferoute.domain.evacuation.graph.dto.response.FloorGraphResponse;
import com.saferoute.domain.evacuation.graph.dto.response.MapEdgeResponse;
import com.saferoute.domain.evacuation.graph.dto.response.MapNodeResponse;
import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.repository.MapGraphRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.floor.repository.FloorRepository;
import com.saferoute.global.api.error.EvacuationErrorCode;
import com.saferoute.global.api.error.FloorErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapGraphService {

    private final MapGraphRepository mapGraphRepository;
    private final FloorRepository floorRepository;

    // 커스텀 편집 UI 초기 로딩용 - 층의 노드/엣지 전체 조회
    public FloorGraphResponse getFloorGraph(UUID floorId) {
        validateFloorExists(floorId);
        List<MapNode> nodes = mapGraphRepository.findNodesByFloor(floorId);
        List<MapEdge> edges = mapGraphRepository.findEdgesByFloor(floorId);
        return FloorGraphResponse.of(nodes, edges);
    }

    // 노드 추가
    @Transactional
    public MapNodeResponse createNode(UUID floorId, CreateMapNodeRequest request) {
        Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new ApiException(FloorErrorCode.FLOOR_NOT_FOUND));
        MapNode node = mapGraphRepository.addNode(floor, request.code(), request.type(), request.name(),
                request.x(), request.y(), request.isExitTarget());
        return MapNodeResponse.from(node);
    }

    // 노드 위치 수정 (드래그 편집)
    @Transactional
    public MapNodeResponse updateNodePosition(UUID nodeId, UpdateMapNodePositionRequest request) {
        MapNode node = findNodeOrThrow(nodeId);
        MapNode updated = mapGraphRepository.updateNodePosition(node, request.x(), request.y());
        return MapNodeResponse.from(updated);
    }

    // 노드 삭제 (연결된 엣지도 함께 삭제)
    @Transactional
    public void deleteNode(UUID nodeId) {
        MapNode node = findNodeOrThrow(nodeId);
        mapGraphRepository.deleteNode(node);
    }

    // 엣지 연결 (fromNode가 속한 floor를 그대로 사용, room-hallway 직접 연결은 Repository에서 검증)
    @Transactional
    public MapEdgeResponse createEdge(CreateMapEdgeRequest request) {
        MapNode fromNode = findNodeOrThrow(request.fromNodeId());
        MapNode toNode = findNodeOrThrow(request.toNodeId());
        MapEdge edge = mapGraphRepository.addEdge(
                fromNode.getFloor(), fromNode, toNode, request.distance(), request.bidirectional());
        return MapEdgeResponse.from(edge);
    }

    // 엣지 삭제
    @Transactional
    public void deleteEdge(UUID edgeId) {
        MapEdge edge = mapGraphRepository.findEdgeById(edgeId)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.MAP_EDGE_NOT_FOUND));
        mapGraphRepository.deleteEdge(edge);
    }

    private MapNode findNodeOrThrow(UUID nodeId) {
        return mapGraphRepository.findNodeById(nodeId)
                .orElseThrow(() -> new ApiException(EvacuationErrorCode.MAP_NODE_NOT_FOUND));
    }

    private void validateFloorExists(UUID floorId) {
        if (!floorRepository.existsById(floorId)) {
            throw new ApiException(FloorErrorCode.FLOOR_NOT_FOUND);
        }
    }
}
