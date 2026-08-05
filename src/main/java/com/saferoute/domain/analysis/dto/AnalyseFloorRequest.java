package com.saferoute.domain.analysis.dto;

public record AnalyseFloorRequest(
    String imageKey,
    double realWidthM,
    double realHeightM
) {

}
