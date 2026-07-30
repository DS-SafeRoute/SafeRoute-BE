package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.FireZone;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FireZoneRepository extends JpaRepository<FireZone, UUID> {
    void deleteAllByFloor_Id(UUID floorId);
}