package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeJpaRepository extends JpaRepository<MapEdge, UUID> {

    List<MapEdge> findAllByFloor_Id(UUID floorId);

    // 특정 노드에 연결된 엣지 (유도등 좌/우 Edge 검증용)
    List<MapEdge> findAllByFromNode_IdOrToNode_Id(UUID fromNodeId, UUID toNodeId);

    void deleteAllByFloor_Id(UUID floorId);
}