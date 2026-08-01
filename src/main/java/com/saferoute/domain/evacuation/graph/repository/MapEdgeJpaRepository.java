package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeJpaRepository extends JpaRepository<MapEdge, UUID> {

    List<MapEdge> findAllByFloor_Id(UUID floorId);

    // 특정 노드에 연결된 엣지 (유도등 좌/우 Edge 검증용, 노드 삭제 시 연결 엣지 조회용으로도 사용)
    List<MapEdge> findAllByFromNode_IdOrToNode_Id(UUID fromNodeId, UUID toNodeId);

    // 노드 삭제 시 연결된 엣지 cascade 삭제용
    void deleteByFromNode_IdOrToNode_Id(UUID fromNodeId, UUID toNodeId);

    // 동일 노드 쌍 중복 엣지 방지용 - 통행은 양방향으로 취급하므로 두 방향 다 체크
    boolean existsByFromNode_IdAndToNode_IdOrFromNode_IdAndToNode_Id(
            UUID fromNodeId1, UUID toNodeId1, UUID fromNodeId2, UUID toNodeId2);
}
