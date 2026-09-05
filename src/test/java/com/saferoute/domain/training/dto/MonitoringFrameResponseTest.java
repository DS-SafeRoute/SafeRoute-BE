package com.saferoute.domain.training.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitoringFrameResponseTest {

    @Test
    void 신규_관측은_이미지_Snapshot_기준_인원과_밀집도와_단계를_반환한다() {
        ObservationItem item = legacyObservation();
        item.setFrameHeadcount(3);
        item.setFrameDensity(0.75);
        item.setFrameCongestionLevel(CongestionLevel.NORMAL);

        MonitoringFrameResponse frame = MonitoringFrameResponse.withoutImage(item);

        assertThat(frame.headcount()).isEqualTo(3);
        assertThat(frame.density()).isEqualTo(0.75);
        assertThat(frame.congestionLevel()).isEqualTo(CongestionLevel.NORMAL);
    }

    @Test
    void 기존_관측은_구간_최대_인원과_평균_밀집도로_fallback한다() {
        MonitoringFrameResponse frame = MonitoringFrameResponse.withoutImage(legacyObservation());

        assertThat(frame.headcount()).isEqualTo(5);
        assertThat(frame.density()).isEqualTo(0.42);
        assertThat(frame.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
    }

    private ObservationItem legacyObservation() {
        return ObservationItem.create(
                UUID.randomUUID(), UUID.randomUUID(), null, "CCTV_001",
                4.0, 5, 10, 0.42, CongestionLevel.CROWDED,
                500L, 1_000L, 1_000L, null, 1L
        );
    }
}
