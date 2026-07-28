package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapNodeJpaRepository extends JpaRepository<MapNode, UUID> {

    List<MapNode> findAllByFloor_Id(UUID floorId);
    void deleteAllByFloor_Id(UUID floorId);
}
