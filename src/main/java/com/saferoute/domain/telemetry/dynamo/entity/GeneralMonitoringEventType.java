package com.saferoute.domain.telemetry.dynamo.entity;

// 혼잡 이벤트(CongestionEventType)와 별도로, BE가 관측값으로부터 직접 판정해서 만드는
// 일반 모니터링 이벤트의 종류. Pi가 이 이벤트들을 위한 별도 API를 호출하지는 않는다.
public enum GeneralMonitoringEventType {
    AI_ANALYSIS_STARTED,
    ROUTE_DEVIATION_DETECTED
}
