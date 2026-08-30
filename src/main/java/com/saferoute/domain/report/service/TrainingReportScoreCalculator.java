package com.saferoute.domain.report.service;

import com.saferoute.domain.report.entity.Grade;
import java.math.BigDecimal;
import java.math.RoundingMode;

// 훈련 리포트의 4개 평가 항목 점수와 최종 등급을 계산하는 순수 계산기.
// 가중치(대피시간 35% / 생존률 30% / 병목 20% / 경로준수율 15%)는 화재예방법 제37조,
// 소방시설법 제16조 등 법적 근거표에서 도출한 값이다.
public final class TrainingReportScoreCalculator {

    public static final double EVACUATION_WEIGHT = 0.35;
    public static final double SURVIVAL_WEIGHT = 0.30;
    public static final double BOTTLENECK_WEIGHT = 0.20;
    public static final double DEVIATION_WEIGHT = 0.15;

    // 병목 1회/시간당 감점 폭. 짧은 훈련과 긴 훈련을 공정하게 비교하기 위해 횟수를 시간당 빈도로
    // 정규화한 뒤 감점한다 (예: 10분짜리 훈련에서 2번 vs 1시간짜리 훈련에서 2번은 심각도가 다르다).
    private static final double BOTTLENECK_PENALTY_PER_EVENT_PER_HOUR = 10.0;

    private TrainingReportScoreCalculator() {
    }

    // 목표 시간 이내면 100점, 목표 시간의 2배 이상이면 0점, 그 사이는 선형 감점.
    public static int evacuationScore(int evacuationSec, int targetEvacuationSec) {
        if (targetEvacuationSec <= 0) {
            return 0;
        }
        if (evacuationSec <= targetEvacuationSec) {
            return 100;
        }
        double overRatio = (double) (evacuationSec - targetEvacuationSec) / targetEvacuationSec;
        double score = 100.0 * (1.0 - overRatio);
        return clamp(Math.round(score));
    }

    // 생존 판정 인원 / 전체 참여 인원 * 100. 그 자체가 0~100 점수다.
    public static BigDecimal survivalRate(int survivorCount, int participantCount) {
        if (participantCount <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = BigDecimal.valueOf(survivorCount)
                .divide(BigDecimal.valueOf(participantCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return clampDecimal(rate);
    }

    // 병목 발생 빈도(시간당)에 비례해 감점한다.
    public static int bottleneckScore(int bottleneckCount, long durationSeconds) {
        double durationHours = durationSeconds > 0 ? durationSeconds / 3600.0 : 1.0;
        double eventsPerHour = bottleneckCount / durationHours;
        double score = 100.0 - eventsPerHour * BOTTLENECK_PENALTY_PER_EVENT_PER_HOUR;
        return clamp(Math.round(score));
    }

    // 이탈률(0.0~1.0)의 반대(준수율)를 점수로 쓴다.
    public static int deviationScore(double deviationRate) {
        double score = (1.0 - deviationRate) * 100.0;
        return clamp(Math.round(score));
    }

    public static double overallScore(int evacuationScore, BigDecimal survivalScore,
                                       int bottleneckScore, int deviationScore) {
        double weighted = evacuationScore * EVACUATION_WEIGHT
                + survivalScore.doubleValue() * SURVIVAL_WEIGHT
                + bottleneckScore * BOTTLENECK_WEIGHT
                + deviationScore * DEVIATION_WEIGHT;
        return BigDecimal.valueOf(weighted).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static Grade gradeOf(double overallScore) {
        if (overallScore >= 90) return Grade.A;
        if (overallScore >= 80) return Grade.B;
        if (overallScore >= 70) return Grade.C;
        if (overallScore >= 60) return Grade.D;
        return Grade.F;
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(100, value));
    }

    private static BigDecimal clampDecimal(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal max = BigDecimal.valueOf(100);
        return value.compareTo(max) > 0 ? max : value;
    }
}
