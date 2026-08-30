package com.saferoute.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// "대피 인원 누적" 차트의 점 하나. cumulativeCount는 실측이 아니라
// (참여 인원 - 그 시점 건물 전체 CCTV 탐지 인원 합)으로 추정한 근사치다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CumulativeEvacuationPoint {

    @Column(name = "elapsed_sec", nullable = false)
    private int elapsedSec;

    @Column(name = "cumulative_count", nullable = false)
    private int cumulativeCount;

    public CumulativeEvacuationPoint(int elapsedSec, int cumulativeCount) {
        this.elapsedSec = elapsedSec;
        this.cumulativeCount = cumulativeCount;
    }
}
