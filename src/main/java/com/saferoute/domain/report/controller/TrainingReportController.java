package com.saferoute.domain.report.controller;

import com.saferoute.domain.report.dto.GenerateReportRequest;
import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.service.TrainingReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

  // 훈련 종료 후 관리자가 참여/생존 인원을 입력하면, 나머지 평가 항목은 BE가 직접 계산해 리포트를 생성한다.
  @PostMapping("/{sessionId}")
  public ResponseEntity<ReportResponse> generateReport(
      @PathVariable UUID sessionId,
      @Valid @RequestBody GenerateReportRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(trainingReportService.generate(sessionId, request, authentication.getName()));
  }
}
