package com.saferoute.domain.floor.repository;

import com.saferoute.domain.floor.entity.Floor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FloorRepository extends JpaRepository<Floor, UUID> {
    List<Floor> findByBuilding_IdOrderByFloorNumAsc(UUID buildingId);
    Optional<Floor> findByIdAndBuilding_Id(UUID id, UUID buildingId);
    boolean existsByBuilding_IdAndFloorNum(UUID buildingId, Integer floorNum);
}