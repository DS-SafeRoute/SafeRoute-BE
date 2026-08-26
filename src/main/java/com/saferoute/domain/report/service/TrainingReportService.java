package com.saferoute.domain.report.service;

import com.saferoute.domain.training.dto.DashboardStatsResponse;
import com.saferoute.domain.report.dto.ReportResponse;
import com.saferoute.domain.report.entity.TrainingReport;
import com.saferoute.domain.report.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.report.repository.TrainingReportRepository;
import com.saferoute.domain.report.dto.CreateReportRequest;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingReportService {

  private final TrainingReportRepository trainingReportRepository;
  private final TrainingSessionRepository trainingSessionRepository;
  private final SchoolContextService schoolContextService;

  public ReportResponse create(CreateReportRequest request, UUID sessionId) {
    TrainingSession session = trainingSessionRepository.findById(sessionId).orElseThrow(
        NoSuchElementException::new);

    TrainingReport report = TrainingReport.create(request.getGrade(),
        request.getSurvivalRate(),
        request.getAvgEvacuationSec(),
        request.getParticipantCount(),
        request.getRiskIndex(),
        request.getAiRecommendations(),
        request.getPdfUrl(),
        session);
    return ReportResponse.from(trainingReportRepository.save(report));
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
