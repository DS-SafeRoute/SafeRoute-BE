package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

  List<TrainingSession> findByStatusAndStartedAtBefore(TrainingStatus status, Instant threshold);
}
