package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.congestion.dto.request.ConnectEventImageRequest;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.EventProcessingStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import com.saferoute.infrastructure.s3.service.S3Service;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CongestionEventImageServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EDGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String CCTV_CODE = "CCTV_001";
    private static final String IMAGE_KEY = "training/" + SESSION_ID + "/events/"
            + CCTV_CODE + "/" + EVENT_ID + ".jpg";

    @InjectMocks
    private CongestionEventImageService service;
    @Mock
    private ObservationRepository observationRepository;
    @Mock
    private DeviceAuthorizationService deviceAuthorizationService;
    @Mock
    private S3Service s3Service;
    @Mock
    private TrainingEventPublisher trainingEventPublisher;

    private final DevicePrincipal principal = new DevicePrincipal(UUID.randomUUID(), CCTV_CODE);
    private final ConnectEventImageRequest request = new ConnectEventImageRequest(IMAGE_KEY, 1_786_500_002_800L);
    private ObservationItem item;

    @BeforeEach
    void setUp() {
        item = ObservationItem.create(
                EVENT_ID, SESSION_ID, EDGE_ID, CCTV_CODE, 5.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, null, 1L
        );
        item.setEventStatus(EventProcessingStatus.PROCESSED);
    }

    @Test
    void 처리된_이벤트에_S3_이미지를_연결하고_업데이트를_발행한다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        given(s3Service.objectExists(IMAGE_KEY)).willReturn(true);
        given(observationRepository.completeImageUpload(
                EVENT_ID.toString(), IMAGE_KEY, request.uploadedAt()
        )).willReturn(true);

        service.connectImage(principal, EVENT_ID, request);

        verify(deviceAuthorizationService).validateCctv(principal, CCTV_CODE);
        verify(observationRepository).completeImageUpload(
                EVENT_ID.toString(), IMAGE_KEY, request.uploadedAt()
        );
        verify(trainingEventPublisher).publishCongestionImageUpdated(SESSION_ID, item);
    }

    @Test
    void 이벤트_POST보다_PATCH가_먼저_도착하면_404를_반환한다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.empty());

        assertError(CongestionErrorCode.EVENT_NOT_FOUND, request);

        verify(s3Service, never()).objectExists(IMAGE_KEY);
    }

    @Test
    void 이벤트가_PROCESSED가_아니면_이미지를_연결하지_않는다() {
        item.setEventStatus(EventProcessingStatus.PROCESSING);
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));

        assertError(CongestionErrorCode.EVENT_NOT_PROCESSED, request);

        verify(s3Service, never()).objectExists(IMAGE_KEY);
    }

    @Test
    void 다른_세션_CCTV_eventId의_object_key를_거부한다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        ConnectEventImageRequest wrongSession = new ConnectEventImageRequest(
                "training/" + UUID.randomUUID() + "/events/" + CCTV_CODE + "/" + EVENT_ID + ".jpg",
                request.uploadedAt()
        );
        ConnectEventImageRequest wrongCctv = new ConnectEventImageRequest(
                "training/" + SESSION_ID + "/events/CCTV_002/" + EVENT_ID + ".jpg",
                request.uploadedAt()
        );
        ConnectEventImageRequest wrongEvent = new ConnectEventImageRequest(
                "training/" + SESSION_ID + "/events/" + CCTV_CODE + "/" + UUID.randomUUID() + ".jpg",
                request.uploadedAt()
        );

        assertError(CongestionErrorCode.EVENT_IMAGE_IDENTITY_MISMATCH, wrongSession);
        assertError(CongestionErrorCode.EVENT_IMAGE_IDENTITY_MISMATCH, wrongCctv);
        assertError(CongestionErrorCode.EVENT_IMAGE_IDENTITY_MISMATCH, wrongEvent);

        verify(s3Service, never()).objectExists(IMAGE_KEY);
    }

    @Test
    void 형식이_잘못된_object_key를_거부한다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        ConnectEventImageRequest malformed = new ConnectEventImageRequest(
                "training/" + SESSION_ID + "/monitoring/" + CCTV_CODE + "/" + EVENT_ID + ".jpg",
                request.uploadedAt()
        );

        assertError(CongestionErrorCode.EVENT_IMAGE_KEY_INVALID, malformed);
    }

    @Test
    void S3에_객체가_없으면_연결하지_않는다() {
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        given(s3Service.objectExists(IMAGE_KEY)).willReturn(false);

        assertError(CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND, request);

        verify(observationRepository, never()).completeImageUpload(
                EVENT_ID.toString(), IMAGE_KEY, request.uploadedAt()
        );
    }

    @Test
    void 동일한_완료_PATCH는_멱등하게_처리하고_이벤트를_재발행한다() {
        item.setEventImageKey(IMAGE_KEY);
        item.setImageUploadedAt(request.uploadedAt());
        item.setImageUploadStatus(ImageUploadStatus.COMPLETED);
        given(observationRepository.findByEventId(EVENT_ID.toString())).willReturn(Optional.of(item));
        given(s3Service.objectExists(IMAGE_KEY)).willReturn(true);
        given(observationRepository.completeImageUpload(
                EVENT_ID.toString(), IMAGE_KEY, request.uploadedAt()
        )).willReturn(false);

        service.connectImage(principal, EVENT_ID, request);

        verify(trainingEventPublisher).publishCongestionImageUpdated(SESSION_ID, item);
    }

    @Test
    void 조건부_갱신_경쟁에서_다른_이미지가_완료되면_409를_반환한다() {
        ObservationItem latest = item;
        latest.setEventImageKey("training/" + SESSION_ID + "/events/" + CCTV_CODE + "/other.jpg");
        latest.setImageUploadedAt(1L);
        latest.setImageUploadStatus(ImageUploadStatus.COMPLETED);
        ObservationItem initial = ObservationItem.create(
                EVENT_ID, SESSION_ID, EDGE_ID, CCTV_CODE, 5.0, 8, 25, 2.5,
                CongestionLevel.CROWDED, 1_000L, 2_000L, 2_000L, null, 1L
        );
        initial.setEventStatus(EventProcessingStatus.PROCESSED);
        given(observationRepository.findByEventId(EVENT_ID.toString()))
                .willReturn(Optional.of(initial), Optional.of(latest));
        given(s3Service.objectExists(IMAGE_KEY)).willReturn(true);
        given(observationRepository.completeImageUpload(
                EVENT_ID.toString(), IMAGE_KEY, request.uploadedAt()
        )).willReturn(false);

        assertError(CongestionErrorCode.EVENT_IMAGE_STATE_CONFLICT, request);
    }

    private void assertError(
            CongestionErrorCode errorCode,
            ConnectEventImageRequest connectRequest
    ) {
        assertThatThrownBy(() -> service.connectImage(principal, EVENT_ID, connectRequest))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }
}
