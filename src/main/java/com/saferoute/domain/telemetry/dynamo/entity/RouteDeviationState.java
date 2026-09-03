package com.saferoute.domain.telemetry.dynamo.entity;

// 유도등+세션 단위의 실시간 경로 이탈 상태. RouteDeviationStateItem에 저장된다.
public enum RouteDeviationState {
    NORMAL,
    DEVIATING
}
