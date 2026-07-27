package com.saferoute.domain.evacuation.graph.repository;

import com.saferoute.domain.evacuation.graph.entity.MapEdge;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeJpaRepository extends JpaRepository<MapEdge, UUID> {

    List<MapEdge> findAllByFloor_Id(UUID floorId);

}
