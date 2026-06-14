package com.saferoute.domain.training.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
public class TrainingReport {

    @Id
    @GeneratedValue
    private UUID id;

    //훈련 상태 (A, B 등)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", length = 20)
    private Grade grade;

    @Column(name = "survival_rate",nullable = false)
    private BigDecimal survivalRate;

    @Column(name = "avg_evacuation_sec")
    private Integer avgEvacuationSec;

    @Column(name="participant_count", nullable = false)
    private Integer participantCount;

    @Column(name = "risk_index",nullable = false)
    private Double riskIndex;

    @Column(name="ai_recommendations", columnDefinition = "TEXT")
    private String aiRecommendations;

    @Column(name="pdf_url", length = 1000)
    private String pdfUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "training_session_id",
      nullable = false,
      unique = true
  )
  private TrainingSession trainingSession;

  public static TrainingReport create(Grade grade,
      BigDecimal survivalRate,
      Integer avgEvacuationSec,
      Integer participantCount,
      Double riskIndex,
      String aiRecommendations,
      String pdfUrl,
      TrainingSession trainingSession) {
    TrainingReport report = new TrainingReport();
    report.grade = grade;
    report.survivalRate = survivalRate;
    report.avgEvacuationSec = avgEvacuationSec;
    report.participantCount = participantCount;
    report.riskIndex = riskIndex;
    report.aiRecommendations = aiRecommendations;
    report.pdfUrl = pdfUrl;
    report.trainingSession = trainingSession;

    return report;
  }
}