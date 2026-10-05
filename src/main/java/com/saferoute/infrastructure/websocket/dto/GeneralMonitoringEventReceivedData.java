package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventType;

// AI_ANALYSIS_STARTED/ROUTE_DEVIATION_DETECTED 저장 직후 1건당 이 메시지 1건을 발행한다.
public record GeneralMonitoringEventReceivedData(
        String eventId,
        String cctvCode,
        GeneralMonitoringEventType eventType,
        Long occurredAt
) {

    public static GeneralMonitoringEventReceivedData from(GeneralMonitoringEventItem item) {
        return new GeneralMonitoringEventReceivedData(
                item.getEventId(),
                item.getCctvCode(),
                item.getEventType(),
                item.getOccurredAt()
        );
    }
}
