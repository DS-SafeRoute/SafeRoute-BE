package com.saferoute.domain.training.repository;

import com.saferoute.domain.training.entity.TrainingReport;
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
      order by tr.createdAt desc
      """)
  List<TrainingReport> findRecentReports(Pageable pageable);

  @Query("SELECT COALESCE(AVG(r.survivalRate), 0), COALESCE(AVG(r.avgEvacuationSec), 0), COALESCE(SUM(r.participantCount), 0) FROM TrainingReport r")
  Object[] getStatistics();
}
