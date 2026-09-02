package com.saferoute.domain.building.repository;

import com.saferoute.domain.building.entity.Building;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuildingRepository
        extends JpaRepository<Building, UUID> {
    List<Building> findAllBySchoolNameOrderByCreatedAtDesc(String schoolName);

    Optional<Building> findByIdAndSchoolName(UUID id, String schoolName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Building b where b.id = :buildingId and b.schoolName = :schoolName")
    Optional<Building> findByIdAndSchoolNameForUpdate(
            @Param("buildingId") UUID buildingId,
            @Param("schoolName") String schoolName);

    // 같은 건물의 서로 다른 세션이 동시에 시작되는 경쟁을 건물 행 하나로 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Building b where b.id = :buildingId")
    Optional<Building> findByIdForUpdate(@Param("buildingId") UUID buildingId);
}
