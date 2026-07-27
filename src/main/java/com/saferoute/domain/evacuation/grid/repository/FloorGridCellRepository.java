package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.FloorGridCell;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorGridCellRepository extends JpaRepository<FloorGridCell, UUID> {
    List<FloorGridCell> findAllByFloor_Id(UUID floorId);
}