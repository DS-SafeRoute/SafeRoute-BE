package com.saferoute.domain.training.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.GeneralMonitoringEventType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MonitoringEventResponseTest {

    private static final String SESSION_ID = UUID.randomUUID().toString();

    @Test
    void AI_분석_시작_이벤트는_INFO_심각도와_고정_문구를_갖는다() {
        GeneralMonitoringEventItem item = GeneralMonitoringEventItem.create(
                "ai-analysis-started:" + SESSION_ID + ":CCTV_001",
                SESSION_ID, "CCTV_001", GeneralMonitoringEventType.AI_ANALYSIS_STARTED, 1_000L, null
        );

        MonitoringEventResponse response = MonitoringEventResponse.fromGeneralEvent(item);

        assertThat(response.type()).isEqualTo(MonitoringEventType.AI_ANALYSIS_STARTED);
        assertThat(response.severity()).isEqualTo(MonitoringEventSeverity.INFO);
        assertThat(response.congestionLevel()).isNull();
        assertThat(response.occurredAt()).isEqualTo(1_000L);
        assertThat(response.cctvCode()).isEqualTo("CCTV_001");
        assertThat(response.message()).isEqualTo("AI 분석 시작 · CCTV_001");
    }

    @Test
    void 경로_이탈_감지_이벤트는_WARNING_심각도와_고정_문구를_갖는다() {
        GeneralMonitoringEventItem item = GeneralMonitoringEventItem.create(
                UUID.randomUUID().toString(),
                SESSION_ID, "CCTV_002", GeneralMonitoringEventType.ROUTE_DEVIATION_DETECTED, 2_000L,
                CongestionLevel.CROWDED
        );

        MonitoringEventResponse response = MonitoringEventResponse.fromGeneralEvent(item);

        assertThat(response.type()).isEqualTo(MonitoringEventType.ROUTE_DEVIATION_DETECTED);
        assertThat(response.severity()).isEqualTo(MonitoringEventSeverity.WARNING);
        assertThat(response.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
        assertThat(response.message()).isEqualTo("경로 이탈 감지 · CCTV_002");
    }
}
