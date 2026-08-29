package com.saferoute.domain.building.repository;

import com.saferoute.domain.building.entity.Building;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository
        extends JpaRepository<Building, UUID> {
    List<Building> findAllBySchoolNameOrderByCreatedAtDesc(String schoolName);

    Optional<Building> findByIdAndSchoolName(UUID id, String schoolName);
}
