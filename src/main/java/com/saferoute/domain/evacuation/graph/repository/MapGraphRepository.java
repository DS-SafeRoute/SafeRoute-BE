package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.UUID;

public interface MapGraphRepository {

    // 노드 추가
    MapNode addNode(Floor floor, String code, NodeType type, String name,
            double x, double y, boolean isExitTarget);

    // 엣지 추가 (계단/방 사이 통로)
    MapEdge addEdge(Floor floor, MapNode fromNode, MapNode toNode, double distance, int capacity, boolean bidirectional);

    // 특정 층의 노드 전체 조회
    List<MapNode> findNodesByFloor(UUID floorId);

    // 특정 층의 엣지 전체 조회
    List<MapEdge> findEdgesByFloor(UUID floorId);
}
