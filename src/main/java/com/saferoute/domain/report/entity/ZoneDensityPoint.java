package com.saferoute.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// "구역별 평균 밀집도" 차트의 막대 하나. avgDensityPercent는 CongestionConfig.veryCrowdedFrom을
// 100%로 두고 실측 밀도(인/㎡)를 정규화한 값이다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZoneDensityPoint {

    @Column(name = "zone_name", nullable = false, length = 50)
    private String zoneName;

    @Column(name = "avg_density_percent", nullable = false)
    private double avgDensityPercent;

    public ZoneDensityPoint(String zoneName, double avgDensityPercent) {
        this.zoneName = zoneName;
        this.avgDensityPercent = avgDensityPercent;
    }
}
