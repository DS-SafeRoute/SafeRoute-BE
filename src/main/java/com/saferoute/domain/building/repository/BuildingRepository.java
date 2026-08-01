package com.saferoute.domain.building.repository;

import com.saferoute.domain.building.entity.Building;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository
        extends JpaRepository<Building, UUID> {
  Optional<Building> findByName(String name);
}