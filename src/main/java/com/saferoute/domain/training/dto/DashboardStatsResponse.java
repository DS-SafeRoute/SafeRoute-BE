package com.saferoute.domain.training.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardStatsResponse {
  private Long totalSessions;
  private Double avgEvacuationSec;
  private Double avgSurvivalRate;
  private Long totalParticipants;
}
