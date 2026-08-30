package com.saferoute.domain.report.dto;

import com.saferoute.domain.report.entity.Grade;
import com.saferoute.domain.report.entity.TrainingReport;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportResponse {
  private String reportId;
  private Grade grade;
  private Double overallScore;
  private Integer avgEvacuationSec;
  private Integer evacuationScore;
  private Integer participantCount;
  private Integer survivorCount;
  private BigDecimal survivalRate;
  private Integer bottleneckCount;
  private Integer bottleneckScore;
  private Double deviationRate;
  private Integer deviationScore;
  private Double riskIndex;
  private String summaryText;
  private List<RecommendationResponse> recommendations;
  private String pdfUrl;
  private ReportChartsResponse charts;

  public static ReportResponse from(TrainingReport report) {
    return ReportResponse.builder()
        .reportId(report.getShortId())
        .grade(report.getGrade())
        .overallScore(report.getOverallScore())
        .avgEvacuationSec(report.getAvgEvacuationSec())
        .evacuationScore(report.getEvacuationScore())
        .participantCount(report.getParticipantCount())
        .survivorCount(report.getSurvivorCount())
        .survivalRate(report.getSurvivalRate())
        .bottleneckCount(report.getBottleneckCount())
        .bottleneckScore(report.getBottleneckScore())
        .deviationRate(report.getDeviationRate())
        .deviationScore(report.getDeviationScore())
        .riskIndex(report.getRiskIndex())
        .summaryText(report.getSummaryText())
        .recommendations(report.getRecommendations().stream()
            .map(RecommendationResponse::from)
            .toList())
        .pdfUrl(report.getPdfUrl())
        .charts(ReportChartsResponse.from(report))
        .build();
  }
}
