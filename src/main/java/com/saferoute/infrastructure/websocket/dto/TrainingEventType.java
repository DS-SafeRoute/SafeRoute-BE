package com.saferoute.infrastructure.websocket.dto;

public enum TrainingEventType {

    // TrainingSession.complete()/stop()/fail() 호출 시점에 발행할 이벤트.
    // 현재는 위 도메인 메서드를 호출하는 시작/종료 API가 없어 실제로 발행되지는 않는다.
    TRAINING_STATUS_UPDATED,

    // 아래 3개는 계약(메시지 규격) 정의만 되어 있는 상태다.
    // 대응하는 도메인 로직(혼잡도 계산, 경로 재계산, 유도등 상태 변경)이 아직 없어
    // 전용 데이터 DTO와 TrainingEventPublisher 발행 메서드는 이번 작업에서 추가하지 않았다.
    CONGESTION_UPDATED,
    EVACUATION_ROUTE_UPDATED,
    IOT_LIGHT_STATUS_UPDATED
}