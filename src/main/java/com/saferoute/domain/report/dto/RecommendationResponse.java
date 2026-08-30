package com.saferoute.domain.report.dto;

import com.saferoute.domain.report.entity.RecommendationPoint;
import com.saferoute.domain.report.entity.RecommendationPriority;

public record RecommendationResponse(
        RecommendationPriority priority,
        String title,
        String description
) {

    public static RecommendationResponse from(RecommendationPoint point) {
        return new RecommendationResponse(point.getPriority(), point.getTitle(), point.getDescription());
    }
}
