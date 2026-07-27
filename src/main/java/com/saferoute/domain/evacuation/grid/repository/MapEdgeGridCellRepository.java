package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MapEdgeGridCellRepository extends JpaRepository<MapEdgeGridCell, UUID> {
    List<MapEdgeGridCell> findAllByGridCell_Id(UUID gridCellId);
    List<MapEdgeGridCell> findAllByMapEdge_Id(UUID mapEdgeId);
}