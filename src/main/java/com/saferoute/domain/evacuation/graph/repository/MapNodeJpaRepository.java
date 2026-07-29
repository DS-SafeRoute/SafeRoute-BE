package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.graph.entity.NodeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapNodeJpaRepository extends JpaRepository<MapNode, UUID> {

    List<MapNode> findAllByFloor_Id(UUID floorId);

    List<MapNode> findAllByFloor_IdAndType(UUID floorId, NodeType type);

    // MapNode.code 순번 생성용 (Service에서 NODE_001 형태로 채번)
    long countByFloor_Id(UUID floorId);

    boolean existsByFloor_IdAndCode(UUID floorId, String code);

    void deleteAllByFloor_Id(UUID floorId);
}