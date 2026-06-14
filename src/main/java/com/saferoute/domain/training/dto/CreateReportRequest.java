package com.saferoute.domain.training.dto;

import com.saferoute.domain.training.Grade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {

  @NotNull
  private Grade grade;

  @NotNull
  private BigDecimal survivalRate;

  @NotNull
  private Integer avgEvacuationSec;

  @NotNull
  private Integer participantCount;

  @NotNull
  private Double riskIndex;

  @NotBlank
  private String aiRecommendations;

  @NotBlank
  private String pdfUrl;

}
