package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

}
