package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.Grade;
import com.saferoute.domain.training.entity.TrainingReport;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReportResponse {
  private Grade grade;
  private BigDecimal survivalRate;
  private Integer avgEvacuationSec;
  private Integer participantCount;
  private Double riskIndex;
  private String aiRecommendations;
  private String pdfUrl;

  public static ReportResponse from(TrainingReport report) {
    return ReportResponse.builder()
        .grade(report.getGrade())
        .survivalRate(report.getSurvivalRate())
        .avgEvacuationSec(report.getAvgEvacuationSec())
        .participantCount(report.getParticipantCount())
        .riskIndex(report.getRiskIndex())
        .aiRecommendations(report.getAiRecommendations())
        .pdfUrl(report.getPdfUrl())
        .build();
  }
}
