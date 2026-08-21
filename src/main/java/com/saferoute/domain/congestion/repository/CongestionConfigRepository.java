package com.saferoute.domain.congestion.repository;

import com.saferoute.domain.congestion.entity.CongestionConfig;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CongestionConfigRepository extends JpaRepository<CongestionConfig, UUID> {

}
