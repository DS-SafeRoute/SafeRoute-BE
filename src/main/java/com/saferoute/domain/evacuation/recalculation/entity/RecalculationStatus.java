package com.saferoute.domain.evacuation.recalculation.entity;

// 혼잡 감지로 트리거된 재탐색 결과의 승인 상태.
// 상태 검증(guard)은 이 엔티티가 아니라 RouteRecalculationService에서 한다
// (TrainingSessionService.start()/end()/forceEnd(), IoTLightService와 동일한 컨벤션 - 엔티티는 단순 세터).
public enum RecalculationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
