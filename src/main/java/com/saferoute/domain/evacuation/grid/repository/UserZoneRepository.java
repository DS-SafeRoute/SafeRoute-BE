package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.UserZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserZoneRepository extends JpaRepository<UserZone, UUID> {

    List<UserZone> findAllByFloor_Id(UUID floorId);

    Optional<UserZone> findByFloor_IdAndName(UUID floorId, String name);

    // 같은 층 안에서만 이름 중복 검사 (다른 층은 같은 이름 허용)
    boolean existsByFloor_IdAndName(UUID floorId, String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from UserZone z where z.floor.id = :floorId")
    int deleteAllByFloorId(@Param("floorId") UUID floorId);
}