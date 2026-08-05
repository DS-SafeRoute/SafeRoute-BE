package com.saferoute.domain.floor.repository;

import com.saferoute.domain.floor.entity.Floor;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FloorRepository extends JpaRepository<Floor, UUID> {
    List<Floor> findByBuilding_IdOrderByFloorNumAsc(UUID buildingId);
    Optional<Floor> findByIdAndBuilding_Id(UUID id, UUID buildingId);
    boolean existsByBuilding_IdAndFloorNum(UUID buildingId, Integer floorNum);

    // 노드 삭제 시 EXIT 카운트 검증과 삭제를 원자적으로 직렬화하기 위한 층 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Floor f where f.id = :id")
    Optional<Floor> findByIdForUpdate(UUID id);
}