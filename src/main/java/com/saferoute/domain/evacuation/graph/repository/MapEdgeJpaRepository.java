package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapEdgeJpaRepository extends JpaRepository<MapEdge, UUID> {

    List<MapEdge> findAllByFloor_Id(UUID floorId);

    Optional<MapEdge> findByIdAndFloor_Building_SchoolName(UUID id, String schoolName);

    // 특정 노드에 연결된 엣지 (유도등 좌/우 Edge 검증용, 노드 삭제/노드 이동 시 연결 엣지의 그리드 셀
    // 재계산용으로도 사용)
    @Query("SELECT e FROM MapEdge e JOIN FETCH e.fromNode JOIN FETCH e.toNode "
            + "WHERE e.fromNode.id = :fromNodeId OR e.toNode.id = :toNodeId")
    List<MapEdge> findAllByFromNode_IdOrToNode_Id(
            @Param("fromNodeId") UUID fromNodeId, @Param("toNodeId") UUID toNodeId);

    // 노드 삭제 시 연결된 엣지 cascade 삭제용
    void deleteByFromNode_IdOrToNode_Id(UUID fromNodeId, UUID toNodeId);

    // 동일 노드 쌍 중복 엣지 방지용 - 통행은 양방향으로 취급하므로 두 방향 다 체크
    boolean existsByFromNode_IdAndToNode_IdOrFromNode_IdAndToNode_Id(
            UUID fromNodeId1, UUID toNodeId1, UUID fromNodeId2, UUID toNodeId2);
    void deleteAllByFloor(Floor floor);
}
