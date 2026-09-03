package com.saferoute.domain.report.dto;

import com.saferoute.domain.report.entity.Grade;
import com.saferoute.domain.report.entity.TrainingReport;
import io.swagger.v3.oas.annotations.media.Schema;
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
  @Schema(
      description = "병목(혼잡) 발생 횟수. 혼잡 이벤트의 상태 전환 개수가 아니라, 실제로 병목 구간이 "
          + "시작된 횟수를 의미한다 - CONGESTION_STARTED이면서 최종 판정된 congestionLevel이 "
          + "CROWDED 또는 VERY_CROWDED인 경우만 1회로 집계하며, 같은 구간의 CONGESTION_LEVEL_UP/"
          + "CONGESTION_ENDED는 포함하지 않는다.",
      example = "3"
  )
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
