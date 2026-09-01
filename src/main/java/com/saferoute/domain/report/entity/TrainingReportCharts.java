package com.saferoute.domain.report.entity;

import java.util.List;

// 리포트 생성 시점에 계산해 함께 저장하는 3개 차트 데이터를 한데 묶은 값 객체
public record TrainingReportCharts(
        List<CumulativeEvacuationPoint> cumulativeEvacuation,
        List<ZoneDensityPoint> zoneDensities,
        List<RecentEvacuationPoint> recentEvacuationTimes
) {
}
