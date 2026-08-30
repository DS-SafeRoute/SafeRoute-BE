package com.saferoute.domain.device.entity;

public enum IoTLightDirection {
    LEFT,
    RIGHT,
    // 평상시(훈련 미진행) 상태 - 양방향 모두 점등
    BOTH,
    OFF
}