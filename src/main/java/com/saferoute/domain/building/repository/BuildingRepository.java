package com.saferoute.domain.building.repository;

import com.saferoute.domain.building.entity.Building;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingRepository
        extends JpaRepository<Building, UUID> {
}