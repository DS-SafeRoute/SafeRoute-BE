package com.saferoute.domain.report.entity;

import com.saferoute.domain.training.entity.TrainingSession;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "training_reports")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    private TrainingReport(Grade grade, BigDecimal survivalRate, Integer avgEvacuationSec,
                           Integer participantCount, Double riskIndex, String aiRecommendations,
                           String pdfUrl, TrainingSession trainingSession) {
        this.grade = grade;
        this.survivalRate = survivalRate;
        this.avgEvacuationSec = avgEvacuationSec;
        this.participantCount = participantCount;
        this.riskIndex = riskIndex;
        this.aiRecommendations = aiRecommendations;
        this.pdfUrl = pdfUrl;
        this.trainingSession = trainingSession;
    }

    public static TrainingReport create(Grade grade, BigDecimal survivalRate, Integer avgEvacuationSec,
                                        Integer participantCount, Double riskIndex, String aiRecommendations,
                                        String pdfUrl, TrainingSession trainingSession) {
        return new TrainingReport(grade, survivalRate, avgEvacuationSec, participantCount,
                riskIndex, aiRecommendations, pdfUrl, trainingSession);
    }
}