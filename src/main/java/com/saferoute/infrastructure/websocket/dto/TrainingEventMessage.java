package com.saferoute.infrastructure.websocket.dto;

import java.time.Instant;
import java.util.UUID;

// 관리자 대시보드로 전달되는 모든 WebSocket 이벤트가 공통으로 따르는 envelope.
public record TrainingEventMessage<T>(
        TrainingEventType eventType,
        UUID sessionId,
        Instant occurredAt,
        T data
) {

    public static <T> TrainingEventMessage<T> of(TrainingEventType eventType, UUID sessionId, T data) {
        return new TrainingEventMessage<>(eventType, sessionId, Instant.now(), data);
    }
}