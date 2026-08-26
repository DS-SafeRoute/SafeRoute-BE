package com.saferoute.domain.report.entity;

import com.saferoute.domain.training.entity.TrainingSession;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.SecureRandom;
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

    // URL에 노출되는 짧은 id. 62^10 조합이라 무작위 생성만으로도 충돌 가능성이 무시할 만큼 낮고,
    // 별도 채번 테이블(CctvCodeAllocation) 없이 컬럼의 UNIQUE 제약을 최종 방어선으로 둔다.
    private static final String SHORT_ID_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHORT_ID_LENGTH = 10;
    private static final SecureRandom SHORT_ID_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "short_id", nullable = false, updatable = false, unique = true, length = SHORT_ID_LENGTH)
    private String shortId;

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
        this.shortId = generateShortId();
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

    private static String generateShortId() {
        StringBuilder builder = new StringBuilder(SHORT_ID_LENGTH);
        for (int i = 0; i < SHORT_ID_LENGTH; i++) {
            builder.append(SHORT_ID_ALPHABET.charAt(SHORT_ID_RANDOM.nextInt(SHORT_ID_ALPHABET.length())));
        }
        return builder.toString();
    }
}