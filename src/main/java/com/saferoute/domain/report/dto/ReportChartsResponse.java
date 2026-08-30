package com.saferoute.domain.report.dto;

import com.saferoute.domain.report.entity.TrainingReport;
import java.util.List;

public record ReportChartsResponse(
        List<CumulativeEvacuationPointResponse> cumulativeEvacuation,
        List<ZoneDensityResponse> zoneDensities,
        List<RecentEvacuationResponse> recentEvacuationTimes
) {

    public static ReportChartsResponse from(TrainingReport report) {
        return new ReportChartsResponse(
                report.getCumulativeEvacuationPoints().stream()
                        .map(p -> new CumulativeEvacuationPointResponse(p.getElapsedSec(), p.getCumulativeCount()))
                        .toList(),
                report.getZoneDensityPoints().stream()
                        .map(p -> new ZoneDensityResponse(p.getZoneName(), p.getAvgDensityPercent()))
                        .toList(),
                report.getRecentEvacuationPoints().stream()
                        .map(p -> new RecentEvacuationResponse(p.getOrdinal(), p.getEvacuationSec()))
                        .toList()
        );
    }

    public record CumulativeEvacuationPointResponse(int elapsedSec, int cumulativeCount) {
    }

    public record ZoneDensityResponse(String zoneName, double avgDensityPercent) {
    }

    public record RecentEvacuationResponse(int ordinal, int evacuationSec) {
    }
}
