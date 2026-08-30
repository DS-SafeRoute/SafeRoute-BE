package com.saferoute.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// "최근 5회 대피 시간" 차트의 점 하나. 같은 건물에서 과거에 생성된 리포트들(오래된 순)에
// 이번 리포트 자신을 마지막에 붙여, 최대 5개까지만 남긴 결과다.
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentEvacuationPoint {

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "evacuation_sec", nullable = false)
    private int evacuationSec;

    public RecentEvacuationPoint(int ordinal, int evacuationSec) {
        this.ordinal = ordinal;
        this.evacuationSec = evacuationSec;
    }
}
