package com.saferoute.domain.telemetry.dynamo.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.saferoute.domain.congestion.entity.CongestionLevel;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class TelemetryItemTest {

    private static final UUID OBSERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID EDGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void 혼잡도와_이벤트_타입은_정의된_계약값만_사용한다() {
        assertThat(CongestionLevel.values()).containsExactly(
                CongestionLevel.NORMAL,
                CongestionLevel.CAUTION,
                CongestionLevel.CROWDED,
                CongestionLevel.VERY_CROWDED
        );
        assertThat(CongestionEventType.values()).containsExactly(
                CongestionEventType.CONGESTION_STARTED,
                CongestionEventType.CONGESTION_LEVEL_UP,
                CongestionEventType.CONGESTION_ENDED
        );
    }

    @Test
    void 관측값은_eventId_기본키와_세션_CCTV_시간_GSI키를_생성한다() {
        ObservationItem item = observation();

        assertThat(item.getPk()).isEqualTo("OBSERVATION#" + OBSERVATION_ID);
        assertThat(item.getSk()).isEqualTo("META");
        assertThat(item.getEdgeId()).isEqualTo(EDGE_ID.toString());
        assertThat(item.getGsi1Pk()).isEqualTo("SESSION#" + SESSION_ID + "#CCTV#CCTV_001");
        assertThat(item.getGsi1Sk()).isEqualTo("TIME#1786500005000");
        assertThat(item.getEventStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
    }

    @Test
    void 관측값은_capturedAt으로부터_30일_TTL을_초단위로_저장한다() {
        ObservationItem item = observation();

        assertThat(item.getExpiresAt())
                .isEqualTo(Math.floorDiv(item.getCapturedAt(), 1_000L) + ObservationItem.TTL_SECONDS);
    }

    @Test
    void 관측값의_수치_타입과_GSI_속성명을_계약대로_매핑한다() {
        var attributes = TableSchema.fromBean(ObservationItem.class)
                .itemToMap(observation(), true);

        assertThat(attributes.get("avgHeadcount").n()).isEqualTo("5.0");
        assertThat(attributes.get("peakHeadcount").n()).isEqualTo("8");
        assertThat(attributes.get("sampleCount").n()).isEqualTo("25");
        assertThat(attributes.get("density").n()).isEqualTo("2.5");
        assertThat(attributes.get("edgeId").s()).isEqualTo(EDGE_ID.toString());
        assertThat(attributes)
                .containsEntry("GSI1_PK", AttributeValue.fromS("SESSION#" + SESSION_ID + "#CCTV#CCTV_001"))
                .containsEntry("GSI1_SK", AttributeValue.fromS("TIME#1786500005000"));
    }

    @Test
    void 혼잡_이벤트는_RECEIVED와_PENDING으로_시작하고_TTL이_없다() {
        CongestionEventItem item = congestionEvent();

        assertThat(item.getPk()).isEqualTo("CONGESTION_EVENT#" + EVENT_ID);
        assertThat(item.getSk()).isEqualTo("META");
        assertThat(item.getGsi1Pk()).isEqualTo("SESSION#" + SESSION_ID);
        assertThat(item.getGsi1Sk()).isEqualTo("EVENT#1786500002300#" + EVENT_ID);
        assertThat(item.getEventStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
        assertThat(item.getImageUploadStatus()).isEqualTo(ImageUploadStatus.PENDING);
        assertThat(TableSchema.fromBean(CongestionEventItem.class).itemToMap(item, true))
                .doesNotContainKey("expiresAt");
    }

    @Test
    void 현재_상태는_세션과_CCTV로_키를_만들고_TTL이_없다() {
        CurrentCctvStateItem item = CurrentCctvStateItem.create(
                SESSION_ID, "CCTV_001", 9, 4.5, CongestionLevel.CROWDED,
                1_786_500_002_300L, 1L
        );

        assertThat(item.getPk()).isEqualTo("CURRENT_STATE#" + SESSION_ID);
        assertThat(item.getSk()).isEqualTo("CCTV#CCTV_001");
        assertThat(TableSchema.fromBean(CurrentCctvStateItem.class).itemToMap(item, true))
                .doesNotContainKey("expiresAt");
    }

    private ObservationItem observation() {
        return ObservationItem.create(
                OBSERVATION_ID, SESSION_ID, EDGE_ID, "CCTV_001", 5.0, 8, 25,
                2.5, CongestionLevel.CAUTION, 1_786_500_000_000L,
                1_786_500_005_000L, 1_786_500_005_000L, null, 1L
        );
    }

    private CongestionEventItem congestionEvent() {
        return CongestionEventItem.received(
                EVENT_ID, SESSION_ID, "CCTV_001",
                CongestionEventType.CONGESTION_STARTED, 1_786_500_002_300L,
                9, 4.5, CongestionLevel.CROWDED, 4.5, CongestionLevel.CROWDED,
                1L, null
        );
    }
}
