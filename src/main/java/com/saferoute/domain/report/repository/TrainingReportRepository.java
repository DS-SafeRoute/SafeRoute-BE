package com.saferoute.domain.report.repository;

import com.saferoute.domain.report.entity.TrainingReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingReportRepository extends JpaRepository<TrainingReport, UUID> {

  // 목록 조회에서 시나리오별 리포트 존재 여부/id를 N+1 없이 계산하기 위해,
  // 시나리오당 세션이 1개뿐이라는 전제로 시나리오 id -> 리포트 shortId를 바로 매핑한다.
  @Query("""
      select tr.trainingSession.scenario.id as scenarioId, tr.shortId as reportId
      from TrainingReport tr
      where tr.trainingSession.scenario.id in :scenarioIds
      """)
  List<ScenarioReportId> findReportIdsByScenarioIds(@Param("scenarioIds") Collection<UUID> scenarioIds);

  interface ScenarioReportId {
    UUID getScenarioId();
    String getReportId();
  }

  // 리포트 단건 조회 URL(shortId)로 조회하되, 기관 소속이 다른 리포트는 보이지 않도록 제한한다.
  Optional<TrainingReport> findByShortIdAndTrainingSession_Scenario_Building_SchoolName(
      String shortId, String schoolName);

  @Query("""
      select tr
      from TrainingReport tr
      join fetch tr.trainingSession ts
      join fetch ts.scenario s
      join s.building b
      where b.schoolName = :schoolName
      order by tr.createdAt desc
      """)
  List<TrainingReport> findRecentReportsBySchoolName(String schoolName, Pageable pageable);

  @Query("""
      select coalesce(avg(r.survivalRate), 0),
             coalesce(avg(r.avgEvacuationSec), 0),
             coalesce(sum(r.participantCount), 0)
      from TrainingReport r
      where r.trainingSession.scenario.building.schoolName = :schoolName
      """)
  List<Object[]> getStatisticsBySchoolName(String schoolName);
}
