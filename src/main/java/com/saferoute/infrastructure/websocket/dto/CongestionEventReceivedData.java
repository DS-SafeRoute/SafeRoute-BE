package com.saferoute.infrastructure.websocket.dto;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import java.util.List;
import java.util.UUID;

// 즉시 혼잡 상태 전환 1건당 이 메시지 1건만 발행한다. 영향받는 Edge가 여러 개여도 반복 발행하지 않고
// affectedEdgeIds 배열에 중복 없이 담는다 (Edge가 없으면 빈 배열, null과 혼용하지 않는다) (이슈 #192).
public record CongestionEventReceivedData(
        String eventId,
        List<UUID> affectedEdgeIds,
        String cctvCode,
        CongestionEventType eventType,
        Double density,
        CongestionLevel congestionLevel,
        Long detectedAt,
        ImageUploadStatus imageUploadStatus
) {

    public static CongestionEventReceivedData from(List<UUID> affectedEdgeIds, CongestionEventItem item) {
        return new CongestionEventReceivedData(
                item.getEventId(),
                affectedEdgeIds.stream().distinct().toList(),
                item.getCctvCode(),
                item.getEventType(),
                item.getDensity(),
                item.getCongestionLevel(),
                item.getDetectedAt(),
                item.getImageUploadStatus()
        );
    }
}
