package com.saferoute.domain.report.repository;

import com.saferoute.domain.report.entity.TrainingReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrainingReportRepository extends JpaRepository<TrainingReport, UUID> {

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
