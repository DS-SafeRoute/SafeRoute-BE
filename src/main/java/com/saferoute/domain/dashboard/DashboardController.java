package com.saferoute.domain.dashboard;

import com.saferoute.domain.training.dto.DashboardStatsResponse;
import com.saferoute.domain.report.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.dto.TrainingStatusResponse;
import com.saferoute.domain.report.service.TrainingReportService;
import com.saferoute.domain.training.service.TrainingSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드", description = "훈련 현황 및 통계 대시보드 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final TrainingReportService trainingReportService;
  private final TrainingSessionService trainingSessionService;

  @GetMapping("/trainings")
  public ResponseEntity<List<RecentTrainingReportResponse>> getRecentReports(
      Authentication authentication
  ) {
    List<RecentTrainingReportResponse> response =
        trainingReportService.getRecentTrainingReport(authentication.getName());
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping("/stats")
  public ResponseEntity<DashboardStatsResponse> getStats(
      Authentication authentication
  ) {
    DashboardStatsResponse response = trainingReportService.getStats(authentication.getName());
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  @GetMapping("/training-status/{sessionId}")
  public ResponseEntity<TrainingStatusResponse> getTrainingStatus(
      @PathVariable UUID sessionId,
      Authentication authentication) {
    return ResponseEntity.ok(trainingSessionService.getTrainingStatus(sessionId, authentication.getName()));
  }

}
