package com.saferoute.infrastructure.websocket.dto;

import java.time.Instant;
import java.util.UUID;

// 층 도면 화면(/topic/floors/{floorId}/lights)으로 전달되는 유도등 이벤트 envelope.
// TrainingEventMessage는 sessionId 고정 필드라 유도등(floorId 기준) 이벤트에 맞지 않아 별도로 둔다.
public record IoTLightEventMessage<T>(
        TrainingEventType eventType,
        UUID floorId,
        Instant occurredAt,
        T data
) {

    public static <T> IoTLightEventMessage<T> of(TrainingEventType eventType, UUID floorId, T data) {
        return new IoTLightEventMessage<>(eventType, floorId, Instant.now(), data);
    }
}
