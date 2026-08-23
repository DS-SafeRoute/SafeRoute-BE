package com.saferoute.domain.evacuation.recalculation.entity;

// 재탐색을 발생시킨 혼잡 이벤트의 종류. ENDED는 우회 경로가 아니라 정상 경로로의 복구 후보를 의미한다.
public enum RecalculationTriggerType {
    STARTED,
    LEVEL_UP,
    ENDED
}
