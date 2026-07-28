package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.IoTLight;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IoTLightJpaRepository extends JpaRepository<IoTLight, UUID> {
    List<IoTLight> findAllByFloor_Id(UUID floorId);
    Optional<IoTLight> findByCode(String code);
    List<IoTLight> findAllByDecisionNode_Id(UUID mapNodeId);
    void deleteAllByFloor_Id(UUID floorId);
}