package com.saferoute.domain.training.service;

import com.saferoute.domain.training.entity.TrainingReport;
import com.saferoute.domain.training.dto.RecentTrainingReportResponse;
import com.saferoute.domain.training.repository.TrainingReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrainingReportService {

  private final TrainingReportRepository trainingReportRepository;

  public List<RecentTrainingReportResponse> getRecentTrainingReport() {
    List<TrainingReport> reports =
        trainingReportRepository.findRecentReports(
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

}
