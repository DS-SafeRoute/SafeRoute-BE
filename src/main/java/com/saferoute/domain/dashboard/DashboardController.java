package com.saferoute.domain.dashboard;

import com.saferoute.domain.training.dto.DashboardStatsResponse;
import com.saferoute.domain.training.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.service.TrainingReportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final TrainingReportService trainingReportService;

  @GetMapping("/trainings")
  public ResponseEntity<List<RecentTrainingReportResponse>> getRecentReports(
  ) {
    List<RecentTrainingReportResponse> response = trainingReportService.getRecentTrainingReport();
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping("/stats")
  public ResponseEntity<DashboardStatsResponse> getStats(
  ) {
    DashboardStatsResponse response = trainingReportService.getStats();
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

}
