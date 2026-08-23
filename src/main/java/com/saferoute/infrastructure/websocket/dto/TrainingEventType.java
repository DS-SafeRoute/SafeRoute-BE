package com.saferoute.infrastructure.websocket.dto;

public enum TrainingEventType {

    // TrainingSession.complete()/stop()/fail() 호출 시점에 발행할 이벤트.
    // 현재는 위 도메인 메서드를 호출하는 시작/종료 API가 없어 실제로 발행되지는 않는다.
    TRAINING_STATUS_UPDATED,

    // CongestionObservationService.reportObservation() 호출 시 발행된다. (이슈 #48/8)
    CONGESTION_UPDATED,

    // 혼잡 이벤트 이미지가 S3 업로드 완료 후 연결되었을 때 발행된다. (이슈 #83)
    CONGESTION_IMAGE_UPDATED,

    // CongestionEventService.reportCongestionEvent() 호출 시 발행된다 (이슈 9, 즉시 혼잡 이벤트).
    CONGESTION_EVENT_RECEIVED,

    // 즉시 혼잡 이벤트의 이미지가 S3 업로드 완료 후 연결되었을 때 발행된다 (이슈 9/11).
    CONGESTION_EVENT_IMAGE_UPDATED,

    // RouteRecalculationService.trigger()가 재탐색 결과를 PENDING으로 저장했을 때 발행된다. (이슈 #48)
    ROUTE_RECALCULATION_REQUESTED,

    // RouteRecalculationService.approve()가 재탐색 결과를 승인했을 때 발행된다. (이슈 #49에서 구현 예정)
    EVACUATION_ROUTE_UPDATED,

    // IoTLightService.changeDirection() 호출 시 TrainingEventPublisher.publishIoTLightStatusUpdated()가 발행한다.
    IOT_LIGHT_STATUS_UPDATED
}
