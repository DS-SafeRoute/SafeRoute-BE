package com.saferoute.domain.report.service;

import com.saferoute.domain.evacuation.deviation.service.RouteDeviationService;
import com.saferoute.domain.evacuation.deviation.service.SessionDeviationResult;
import com.saferoute.domain.report.dto.GenerateReportRequest;
import com.saferoute.domain.report.entity.Grade;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.training.dto.DashboardStatsResponse;
import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.entity.TrainingReportCharts;
import com.saferoute.domain.report.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.ReportErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrainingReportService {

  // 세션 하나에서 나올 수 있는 병목 이벤트 수의 실질적 상한. DynamoDB 쿼리는 한 번에 다 끌어와야 정확한
  // 횟수를 셀 수 있어, 기본 페이지 제한(100)보다 넉넉하게 잡는다.
  private static final int BOTTLENECK_QUERY_LIMIT = 5_000;

  private final TrainingReportRepository trainingReportRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final CongestionEventRepository congestionEventRepository;
  private final RouteDeviationService routeDeviationService;
  private final TrainingReportChartService trainingReportChartService;
  private final SchoolContextService schoolContextService;

  // 훈련 종료 후 관리자가 참여/생존 인원만 입력하면, 나머지 3개 항목(대피시간/병목/경로준수율)은
  // 이미 갖고 있는 데이터로 직접 계산해 리포트를 생성한다.
  @Transactional
  public ReportResponse generate(UUID sessionId, GenerateReportRequest request, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    TrainingSession session = trainingSessionRepository
        .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
        .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));

    if (session.getEndedAt() == null) {
      throw new ApiException(ReportErrorCode.SESSION_NOT_ENDED);
    }
    if (trainingReportRepository.existsByTrainingSession_Id(sessionId)) {
      throw new ApiException(ReportErrorCode.REPORT_ALREADY_EXISTS);
    }
    if (request.survivorCount() > request.participantCount()) {
      throw new ApiException(ReportErrorCode.SURVIVOR_COUNT_EXCEEDS_PARTICIPANTS);
    }

    int evacuationSec = (int) Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
    int targetEvacuationSec = session.getScenario().getTargetEvacuationSec();
    int evacuationScore = TrainingReportScoreCalculator.evacuationScore(evacuationSec, targetEvacuationSec);

    BigDecimal survivalRate = TrainingReportScoreCalculator.survivalRate(
        request.survivorCount(), request.participantCount());

    int bottleneckCount = congestionEventRepository
        .findAllBySessionId(sessionId.toString(), BOTTLENECK_QUERY_LIMIT)
        .size();
    int bottleneckScore = TrainingReportScoreCalculator.bottleneckScore(bottleneckCount, evacuationSec);

    SessionDeviationResult deviation = routeDeviationService.calculateForSession(sessionId, email);
    int deviationScore = TrainingReportScoreCalculator.deviationScore(deviation.deviationRate());

    double overallScore = TrainingReportScoreCalculator.overallScore(
        evacuationScore, survivalRate, bottleneckScore, deviationScore);
    Grade grade = TrainingReportScoreCalculator.gradeOf(overallScore);

    TrainingReportCharts charts = new TrainingReportCharts(
        trainingReportChartService.buildCumulativeEvacuation(session, request.participantCount()),
        trainingReportChartService.buildZoneDensities(session),
        trainingReportChartService.buildRecentEvacuationTimes(session.getScenario().getBuildingId(), evacuationSec));

    TrainingReport report = TrainingReport.create(
        grade, overallScore,
        evacuationSec, evacuationScore,
        request.participantCount(), request.survivorCount(), survivalRate,
        bottleneckCount, bottleneckScore,
        deviation.deviationRate(), deviationScore,
        charts,
        session);

    try {
      return ReportResponse.from(trainingReportRepository.saveAndFlush(report));
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ReportErrorCode.REPORT_ALREADY_EXISTS);
    }
  }

  public ReportResponse getReport(String reportId, String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    TrainingReport report = trainingReportRepository
        .findByShortIdAndTrainingSession_Scenario_Building_SchoolName(reportId, schoolName)
        .orElseThrow(() -> new ApiException(ReportErrorCode.REPORT_NOT_FOUND));
    return ReportResponse.from(report);
  }

  public List<RecentTrainingReportResponse> getRecentTrainingReport(String email) {
    String schoolName = schoolContextService.getSchoolName(email);
    List<TrainingReport> reports =
        trainingReportRepository.findRecentReportsBySchoolName(
            schoolName,
            PageRequest.of(0, 5)
        );
    return reports.stream()
        .map(report -> new RecentTrainingReportResponse(
            report.getTrainingSession()
                .getScenario()
                .getName(),
            report.getTrainingSession()
                .getStartedAt(),
            report.getParticipantCount(),
            report.getAvgEvacuationSec(),
            report.getSurvivalRate(),
            report.getGrade()
        ))
        .toList();
  }

  public DashboardStatsResponse getStats(String email) {
    String schoolName = schoolContextService.getSchoolName(email);

    long totalSessions = trainingSessionRepository.countByScenario_Building_SchoolName(schoolName);

    Object[] result = trainingReportRepository.getStatisticsBySchoolName(schoolName).get(0);
    Double avgSurvivalRate = result[0] != null ? ((Number) result[0]).doubleValue() : 0.0;
    Double avgEvacuationSec = result[1] != null ? ((Number) result[1]).doubleValue() : 0.0;
    Long totalParticipants = result[2] != null ? ((Number) result[2]).longValue() : 0L;

    return new DashboardStatsResponse(totalSessions, avgEvacuationSec, avgSurvivalRate,
        totalParticipants);
  }

}
