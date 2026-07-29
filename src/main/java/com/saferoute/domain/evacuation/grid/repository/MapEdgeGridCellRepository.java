package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.MapEdgeGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MapEdgeGridCellRepository extends JpaRepository<MapEdgeGridCell, UUID> {

    // 화재/혼잡이 발생한 셀이 어떤 Edge에 영향을 주는지
    List<MapEdgeGridCell> findAllByGridCell_Id(UUID gridCellId);

    List<MapEdgeGridCell> findAllByGridCell_IdIn(List<UUID> gridCellIds);

    List<MapEdgeGridCell> findAllByMapEdge_Id(UUID mapEdgeId);
}