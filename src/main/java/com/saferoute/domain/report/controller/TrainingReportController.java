package com.saferoute.domain.report.controller;

import com.saferoute.domain.report.dto.CreateReportRequest;
import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.service.TrainingReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "훈련 리포트", description = "훈련 세션 결과 리포트 생성 API")
@RestController
@RequestMapping("/api/v1/analysis/trainings")
@RequiredArgsConstructor
public class TrainingReportController {
  private final TrainingReportService trainingReportService;

  @PostMapping("/{sessionId}")
  public ResponseEntity<ReportResponse> createReport(
      @PathVariable UUID sessionId,
      @Valid @RequestBody CreateReportRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trainingReportService.create(request, sessionId));
  }


}
