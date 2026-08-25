package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.saferoute.domain.congestion.dto.response.CongestionImageUrlResponse;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CongestionImageUrlServiceTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @InjectMocks
    private CongestionImageUrlService service;

    @Mock
    private CongestionEventRepository congestionEventRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private S3PresignedUrlService s3PresignedUrlService;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private SchoolContextService schoolContextService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(schoolContextService.getSchoolName(EMAIL))
                .thenReturn(SCHOOL_NAME);
        org.mockito.Mockito.lenient().when(
                trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                        SESSION_ID, SCHOOL_NAME))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(TrainingSession.class)));
    }

    @Test
    void 완료된_이벤트_이미지의_조회_URL을_발급한다() {
        CongestionEventItem item = CongestionEventItem.received(
                EVENT_ID, SESSION_ID, "CCTV_001", CongestionEventType.CONGESTION_STARTED,
                2_000L, 9, 4.5, CongestionLevel.CROWDED, 4.5, CongestionLevel.CROWDED, 1L,
                "training/s/events/CCTV_001/e.jpg"
        );
        item.setImageUploadStatus(ImageUploadStatus.COMPLETED);
        given(congestionEventRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        Instant expiresAt = Instant.parse("2026-08-24T00:05:00Z");
        given(s3PresignedUrlService.createGetUrl("training/s/events/CCTV_001/e.jpg"))
                .willReturn(new PresignedGetUrl("https://example.com/view", expiresAt));

        CongestionImageUrlResponse response = service.getEventImageUrl(EVENT_ID, EMAIL);

        assertThat(response.imageUrl()).isEqualTo("https://example.com/view");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void 다른_기관의_이벤트_이미지는_조회할_수_없다() {
        CongestionEventItem item = CongestionEventItem.received(
                EVENT_ID, SESSION_ID, "CCTV_001", CongestionEventType.CONGESTION_STARTED,
                2_000L, 9, 4.5, CongestionLevel.CROWDED, 4.5, CongestionLevel.CROWDED, 1L,
                "training/s/events/CCTV_001/e.jpg"
        );
        given(congestionEventRepository.findByEventId(EVENT_ID.toString()))
                .willReturn(Optional.of(item));
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventImageUrl(EVENT_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 이벤트를_찾을_수_없으면_EVENT_NOT_FOUND를_던진다() {
        given(congestionEventRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventImageUrl(EVENT_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 이벤트_이미지가_아직_완료되지_않았으면_EVENT_IMAGE_OBJECT_NOT_FOUND를_던진다() {
        CongestionEventItem item = CongestionEventItem.received(
                EVENT_ID, SESSION_ID, "CCTV_001", CongestionEventType.CONGESTION_STARTED,
                2_000L, 9, 4.5, CongestionLevel.CROWDED, 4.5, CongestionLevel.CROWDED, 1L, null
        );
        given(congestionEventRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));

        assertThatThrownBy(() -> service.getEventImageUrl(EVENT_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND);
    }

    @Test
    void 관측값_이미지의_조회_URL을_발급한다() {
        ObservationItem item = ObservationItem.create(
                EVENT_ID, SESSION_ID, null, "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CAUTION, 1_000L, 2_000L, 2_000L,
                "training/s/monitoring/CCTV_001/2000.jpg", 1L
        );
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        Instant expiresAt = Instant.parse("2026-08-24T00:05:00Z");
        given(s3PresignedUrlService.createGetUrl("training/s/monitoring/CCTV_001/2000.jpg"))
                .willReturn(new PresignedGetUrl("https://example.com/view", expiresAt));

        CongestionImageUrlResponse response = service.getObservationImageUrl(EVENT_ID, EMAIL);

        assertThat(response.imageUrl()).isEqualTo("https://example.com/view");
    }

    @Test
    void 관측값_이미지_키가_없으면_EVENT_IMAGE_OBJECT_NOT_FOUND를_던진다() {
        ObservationItem item = ObservationItem.create(
                EVENT_ID, SESSION_ID, null, "CCTV_001", 5.0, 8, 25, 2.5,
                CongestionLevel.CAUTION, 1_000L, 2_000L, 2_000L, null, 1L
        );
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));

        assertThatThrownBy(() -> service.getObservationImageUrl(EVENT_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND);
    }

    @Test
    void 관측값을_찾을_수_없으면_EVENT_NOT_FOUND를_던진다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getObservationImageUrl(EVENT_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CongestionErrorCode.EVENT_NOT_FOUND);
    }
}
