package com.saferoute.infrastructure.websocket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.congestion.dto.response.ObservationResponse;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CongestionEventDataTest {

    private ObservationItem observationItem(String monitoringImageKey) {
        return ObservationItem.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "CCTV_001",
                8.6,
                12,
                25,
                0.42,
                CongestionLevel.CROWDED,
                1_000L,
                2_000L,
                2_000L,
                monitoringImageKey,
                1L
        );
    }

    @Test
    @DisplayName("영향받는 Edge 전체를 edgeIds 배열로, density를 포함해서 매핑한다 (REST ObservationResponse와 필드 일치)")
    void from_mapsAllEdgesAndDensity() {
        ObservationItem item = observationItem(null);
        UUID edgeA = UUID.randomUUID();
        UUID edgeB = UUID.randomUUID();

        CongestionEventData data = CongestionEventData.from(List.of(edgeA, edgeB), item);
        ObservationResponse restResponse = ObservationResponse.from(item);

        assertThat(data.eventId()).isEqualTo(UUID.fromString(item.getEventId()));
        assertThat(data.edgeIds()).containsExactly(edgeA, edgeB);
        assertThat(data.cctvCode()).isEqualTo(restResponse.cctvCode());
        assertThat(data.avgHeadcount()).isEqualTo(restResponse.avgHeadcount());
        assertThat(data.peakHeadcount()).isEqualTo(restResponse.peakHeadcount());
        assertThat(data.density()).isEqualTo(restResponse.density());
        assertThat(data.congestionLevel()).isEqualTo(restResponse.congestionLevel());
        assertThat(data.capturedAt()).isEqualTo(restResponse.capturedAt());
    }

    @Test
    @DisplayName("영향받는 Edge가 없으면 빈 목록으로 매핑한다")
    void from_mapsEmptyEdgeList() {
        ObservationItem item = observationItem(null);

        CongestionEventData data = CongestionEventData.from(List.of(), item);

        assertThat(data.edgeIds()).isEmpty();
    }

    @Test
    @DisplayName("monitoringImageKey가 있으면 hasMonitoringImage=true, 없으면 false로 매핑한다")
    void from_mapsHasMonitoringImage() {
        ObservationItem withImage = observationItem("training/session/monitoring/CCTV_001/2000.jpg");
        ObservationItem withoutImage = observationItem(null);

        assertThat(CongestionEventData.from(List.of(), withImage).hasMonitoringImage()).isTrue();
        assertThat(CongestionEventData.from(List.of(), withoutImage).hasMonitoringImage()).isFalse();
    }
}
