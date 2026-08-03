package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapGraphRepository {

    // 노드 추가
    MapNode addNode(Floor floor, String code, NodeType type, String name,
            double x, double y, boolean isExitTarget);

    // 엣지 추가 (계단/방 사이 통로)
    MapEdge addEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance, boolean bidirectional);

    // 특정 층의 노드 전체 조회
    List<MapNode> findNodesByFloor(UUID floorId);

    // 특정 층의 엣지 전체 조회
    List<MapEdge> findEdgesByFloor(UUID floorId);

    // 노드 단건 조회 (수정/삭제/엣지 생성 시 참조용)
    Optional<MapNode> findNodeById(UUID nodeId);

    // 해당 층의 EXIT 대상(isExitTarget=true) 노드 개수 - 마지막 EXIT 삭제 방지 체크용
    long countExitTargetNodesByFloor(UUID floorId);

    // 엣지 단건 조회 (삭제 시 참조용)
    Optional<MapEdge> findEdgeById(UUID edgeId);

    // 노드 위치 수정 (드래그 편집)
    MapNode updateNodePosition(MapNode node, double x, double y);

    // 노드 삭제 - 연결된 엣지도 함께 삭제(cascade)
    void deleteNode(MapNode node);

    // 엣지 삭제
    void deleteEdge(MapEdge edge);
}
