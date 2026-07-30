package com.saferoute.domain.training.entity;

// 화재 확산 속도.
// 시뮬레이션 사용 방식: 확산 tick 간격을 결정
//   FAST > MEDIUM > SLOW 순으로 인접 셀로 번지는 주기가 짧게
public enum FireSpreadSpeed {
    SLOW,
    MEDIUM,
    FAST
}