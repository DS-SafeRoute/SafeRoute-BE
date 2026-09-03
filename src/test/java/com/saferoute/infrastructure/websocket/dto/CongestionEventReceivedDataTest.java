package com.saferoute.infrastructure.websocket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CongestionEventReceivedDataTest {

    private CongestionEventItem congestionEventItem() {
        return CongestionEventItem.received(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CCTV_001",
                CongestionEventType.CONGESTION_STARTED,
                2_000L,
                13,
                4.5,
                CongestionLevel.CROWDED,
                3.25,
                CongestionLevel.CROWDED,
                1L,
                null
        );
    }

    @Test
    @DisplayName("영향받는 Edge 전체를 affectedEdgeIds 배열로 매핑한다")
    void from_mapsAllEdges() {
        CongestionEventItem item = congestionEventItem();
        UUID edgeA = UUID.randomUUID();
        UUID edgeB = UUID.randomUUID();

        CongestionEventReceivedData data = CongestionEventReceivedData.from(List.of(edgeA, edgeB), item);

        assertThat(data.eventId()).isEqualTo(item.getEventId());
        assertThat(data.affectedEdgeIds()).containsExactly(edgeA, edgeB);
        assertThat(data.cctvCode()).isEqualTo(item.getCctvCode());
        assertThat(data.eventType()).isEqualTo(item.getEventType());
        assertThat(data.density()).isEqualTo(item.getDensity());
        assertThat(data.congestionLevel()).isEqualTo(item.getCongestionLevel());
        assertThat(data.detectedAt()).isEqualTo(item.getDetectedAt());
        assertThat(data.imageUploadStatus()).isEqualTo(item.getImageUploadStatus());
    }

    @Test
    @DisplayName("영향받는 Edge가 없으면 빈 목록으로 매핑한다")
    void from_mapsEmptyEdgeList() {
        CongestionEventReceivedData data = CongestionEventReceivedData.from(List.of(), congestionEventItem());

        assertThat(data.affectedEdgeIds()).isEmpty();
    }

    @Test
    @DisplayName("같은 Edge ID가 중복으로 들어와도 한 번만 담는다")
    void from_dedupesEdgeIds() {
        UUID edgeA = UUID.randomUUID();

        CongestionEventReceivedData data =
                CongestionEventReceivedData.from(List.of(edgeA, edgeA), congestionEventItem());

        assertThat(data.affectedEdgeIds()).containsExactly(edgeA);
    }
}
