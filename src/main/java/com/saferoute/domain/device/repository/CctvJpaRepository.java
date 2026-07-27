package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.Cctv;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CctvJpaRepository extends JpaRepository<Cctv, UUID> {
    List<Cctv> findAllByFloor_Id(UUID floorId);
    Optional<Cctv> findByCode(String code);
    List<Cctv> findAllByMonitoredEdge_Id(UUID mapEdgeId);
}