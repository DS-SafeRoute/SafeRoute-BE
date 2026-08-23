package com.saferoute.domain.evacuation.recalculation.entity;

// 혼잡 감지로 트리거된 재탐색 결과의 승인 상태.
// 상태 검증(guard)은 이 엔티티가 아니라 RouteRecalculationService에서 한다
// (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션 - 엔티티는 단순 세터).
public enum RecalculationStatus {
    PENDING,
    APPROVED,
    REJECTED,

    // 승인 전에 혼잡 상태가 바뀌었거나(레벨 변동), 훈련이 종료되었거나, 혼잡이 먼저 끝나서
    // 더 이상 유효하지 않게 된 후보. RouteRecalculationService가 시스템적으로만 부여한다.
    CANCELLED
}
