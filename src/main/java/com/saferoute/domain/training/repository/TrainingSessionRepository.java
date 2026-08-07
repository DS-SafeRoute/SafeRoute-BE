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

  // 혼잡 이벤트가 들어온 엣지가 속한 건물에 현재 진행 중인 세션이 있는지 조회한다.
  // 건물당 동시 RUNNING 세션은 1개라고 가정한다 (TrainingSession에 세션-층 직접 연결이 없어 건물 단위로 조회).
  Optional<TrainingSession> findFirstByStatusAndScenario_Building_Id(TrainingStatus status, UUID buildingId);
}
