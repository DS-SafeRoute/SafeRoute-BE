package com.saferoute.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// "개선 권고사항" 카드 하나. 고정 템플릿(TrainingReportNarrativeGenerator)이 점수를 기준으로 생성한다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationPoint {

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private RecommendationPriority priority;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "description", nullable = false, length = 300)
    private String description;

    public RecommendationPoint(RecommendationPriority priority, String title, String description) {
        this.priority = priority;
        this.title = title;
        this.description = description;
    }
}
