package com.saferoute.domain.evacuation.grid.repository;

import com.saferoute.domain.evacuation.grid.entity.UserZone;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserZoneRepository extends JpaRepository<UserZone, UUID> {

    List<UserZone> findAllByFloor_Id(UUID floorId);

    Optional<UserZone> findByFloor_IdAndName(UUID floorId, String name);

    // 같은 층 안에서만 이름 중복 검사 (다른 층은 같은 이름 허용)
    boolean existsByFloor_IdAndName(UUID floorId, String name);

    void deleteAllByFloor_Id(UUID floorId);
}