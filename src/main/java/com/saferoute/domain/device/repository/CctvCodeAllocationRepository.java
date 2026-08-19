package com.saferoute.domain.device.repository;

import com.saferoute.domain.device.entity.CctvCodeAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CctvCodeAllocationRepository extends JpaRepository<CctvCodeAllocation, Long> {
}
