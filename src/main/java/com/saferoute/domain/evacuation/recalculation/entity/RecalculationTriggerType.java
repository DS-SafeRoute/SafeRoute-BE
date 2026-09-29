package com.saferoute.domain.evacuation.recalculation.entity;

// 재탐색을 발생시킨 이벤트의 종류. ENDED는 우회 경로가 아니라 정상 경로로의 복구 후보를 의미한다.
// FIRE_SPREAD는 혼잡과 무관하게 화재가 번진 구간을 완전 제외하고 다시 계산한 우회 후보다.
public enum RecalculationTriggerType {
    STARTED,
    LEVEL_UP,
    ENDED,
    FIRE_SPREAD
}
