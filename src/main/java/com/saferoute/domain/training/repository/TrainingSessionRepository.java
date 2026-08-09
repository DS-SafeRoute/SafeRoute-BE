package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

  List<TrainingSession> findByStatusAndStartedAtBefore(TrainingStatus status, Instant threshold);

  // 혼잡 이벤트가 들어온 엣지가 속한 건물에 현재 진행 중인 세션이 있는지 조회한다.
  // 건물당 동시 RUNNING 세션은 1개라고 가정한다 (TrainingSession에 세션-층 직접 연결이 없어 건물 단위로 조회).
  // 이 가정이 실제로 DB 제약으로 강제되진 않으므로(건물↔세션이 시나리오를 거쳐 조인되어 단순 유니크 인덱스로 표현 불가),
  // 여러 RUNNING 세션이 동시에 존재하는 예외적인 상황에서도 결과가 흔들리지 않도록 시작 시각 기준으로 정렬한다.
  Optional<TrainingSession> findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(
      @Param("status") TrainingStatus status, @Param("buildingId") UUID buildingId);
}
