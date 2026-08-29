package com.saferoute.domain.report.controller;

import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.service.TrainingReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 리포트", description = "훈련 리포트 단건 조회 API")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

  private final TrainingReportService trainingReportService;

  // reportId는 TrainingReport.shortId (URL 노출용 짧은 id)
  @GetMapping("/{reportId}")
  public ResponseEntity<ReportResponse> getReport(
      @PathVariable String reportId,
      Authentication authentication) {
    return ResponseEntity.ok(trainingReportService.getReport(reportId, authentication.getName()));
  }
}
