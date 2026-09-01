package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

  List<TrainingSession> findByStatusAndStartedAtBefore(TrainingStatus status, Instant threshold);

  List<TrainingSession> findAllByStatus(TrainingStatus status);
  // 모니터링 화면 진입점: 요청자 학교 소속 세션 중 상태로 필터링해 최신 시작 순으로 반환한다.
  @EntityGraph(attributePaths = {"scenario", "scenario.building"})
  List<TrainingSession> findAllByStatusAndScenario_Building_SchoolNameOrderByStartedAtDesc(
      TrainingStatus status, String schoolName);

  // Pi가 보낸 UUID 세션이 실행 중이고, 혼잡 엣지와 같은 건물에 속하는지 한 번에 검증한다.
  Optional<TrainingSession> findByIdAndStatusAndScenario_Building_Id(
      UUID id, TrainingStatus status, UUID buildingId);

  // Pi의 설정 조회 요청엔 세션 id가 없어 건물만으로 현재 RUNNING 세션을 찾는다.
  // 건물당 동시 RUNNING 세션은 TrainingSessionService가 건물 행 잠금 후 강제한다.
  // 레거시/직접 SQL로 중복 데이터가 생겨도 결과가 흔들리지 않도록 시작 시각 기준으로 정렬한다.
  Optional<TrainingSession> findFirstByStatusAndScenario_Building_IdOrderByStartedAtAsc(
      TrainingStatus status, UUID buildingId);

  boolean existsByStatusAndScenario_Building_Id(TrainingStatus status, UUID buildingId);

  Optional<TrainingSession> findByIdAndScenario_Building_SchoolName(UUID id, String schoolName);

  long countByScenario_Building_SchoolName(String schoolName);

  boolean existsByScenario_Id(UUID scenarioId);

  // 목록 조회에서 시나리오별로 deletable을 N+1 없이 계산하기 위해, 세션이 하나라도 있는 시나리오 id만 모아서 가져온다.
  @Query("select distinct s.scenario.id from TrainingSession s where s.scenario.id in :scenarioIds")
  Set<UUID> findScenarioIdsWithAnySession(@Param("scenarioIds") Collection<UUID> scenarioIds);
}
