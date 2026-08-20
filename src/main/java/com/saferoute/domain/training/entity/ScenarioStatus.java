package com.saferoute.domain.training.entity;

// 시나리오 진행 상태. 연결된 TrainingSession의 생명주기 이벤트에 맞춰 전이된다
// (TrainingSessionService.start()/end()/forceEnd()/failTimedOutSessions()에서 갱신).
public enum ScenarioStatus {
    DRAFT,
    READY,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}
