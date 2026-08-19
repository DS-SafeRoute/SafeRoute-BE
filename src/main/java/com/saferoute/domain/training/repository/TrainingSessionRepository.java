package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

  List<TrainingSession> findByStatusAndStartedAtBefore(TrainingStatus status, Instant threshold);

  // Pi가 보낸 UUID 세션이 실행 중이고, 혼잡 엣지와 같은 건물에 속하는지 한 번에 검증한다.
  Optional<TrainingSession> findByIdAndStatusAndScenario_Building_Id(
      UUID id, TrainingStatus status, UUID buildingId);
}
