package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.LatestMonitoringCaptureItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.LatestMonitoringCaptureRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.dto.MonitoringFrameListResponse;
import com.saferoute.domain.training.dto.MonitoringFrameResponse;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.error.CctvErrorCode;
import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrainingMonitoringServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUILDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CCTV_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String EMAIL = "manager@saferoute.com";
    private static final String SCHOOL_NAME = "SafeRoute School";

    @Mock
    private TrainingSessionRepository trainingSessionRepository;
    @Mock
    private CctvJpaRepository cctvJpaRepository;
    @Mock
    private LatestMonitoringCaptureRepository latestMonitoringCaptureRepository;
    @Mock
    private ObservationRepository observationRepository;
    @Mock
    private S3PresignedUrlService s3PresignedUrlService;
    @Mock
    private SchoolContextService schoolContextService;

    private TrainingMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new TrainingMonitoringService(
                trainingSessionRepository,
                cctvJpaRepository,
                latestMonitoringCaptureRepository,
                observationRepository,
                s3PresignedUrlService,
                schoolContextService
        );
        given(schoolContextService.getSchoolName(EMAIL)).willReturn(SCHOOL_NAME);
    }

    @Test
    void 활성_CCTV의_최신_캡처와_presigned_URL을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(3);
        LatestMonitoringCaptureItem capture = org.mockito.Mockito.mock(LatestMonitoringCaptureItem.class);
        Instant expiresAt = Instant.parse("2026-08-27T01:00:00Z");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(capture));
        given(capture.getCctvCode()).willReturn("CCTV_001");
        given(capture.getMonitoringImageKey())
                .willReturn("training/session/monitoring/CCTV_001/1787722095000.jpg");
        given(capture.getCapturedAt()).willReturn(1_787_722_095_000L);
        given(s3PresignedUrlService.createGetUrl(
                "training/session/monitoring/CCTV_001/1787722095000.jpg"))
                .willReturn(new PresignedGetUrl("https://example.com/frame.jpg", expiresAt));

        MonitoringCameraListResponse response = service.getCameras(SESSION_ID, EMAIL);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.cameras()).hasSize(1);
        MonitoringCameraResponse camera = response.cameras().get(0);
        assertThat(camera.cctvId()).isEqualTo(CCTV_ID);
        assertThat(camera.code()).isEqualTo("CCTV_001");
        assertThat(camera.name()).isEqualTo("CAM-1");
        assertThat(camera.buildingName()).isEqualTo("A동");
        assertThat(camera.floorName()).isEqualTo("3층");
        assertThat(camera.location()).isEqualTo("A동 3층");
        assertThat(camera.thumbnailUrl()).isEqualTo("https://example.com/frame.jpg");
        assertThat(camera.capturedAt()).isEqualTo(1_787_722_095_000L);
        assertThat(camera.urlExpiresAt()).isEqualTo(expiresAt.toEpochMilli());
    }

    @Test
    void 캡처가_없는_활성_CCTV도_null_이미지_필드로_포함한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(-1);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of());

        MonitoringCameraResponse camera = service.getCameras(SESSION_ID, EMAIL)
                .cameras().get(0);

        assertThat(camera.floorName()).isEqualTo("지하 1층");
        assertThat(camera.location()).isEqualTo("A동 지하 1층");
        assertThat(camera.thumbnailUrl()).isNull();
        assertThat(camera.capturedAt()).isNull();
        assertThat(camera.urlExpiresAt()).isNull();
        verify(s3PresignedUrlService, never()).createGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 최신_관측의_이미지_키가_공백이면_placeholder_응답을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(1);
        LatestMonitoringCaptureItem capture = org.mockito.Mockito.mock(LatestMonitoringCaptureItem.class);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(capture));
        given(capture.getCctvCode()).willReturn("CCTV_001");
        given(capture.getMonitoringImageKey()).willReturn(" ");

        MonitoringCameraResponse camera = service.getCameras(SESSION_ID, EMAIL)
                .cameras().get(0);

        assertThat(camera.thumbnailUrl()).isNull();
        assertThat(camera.capturedAt()).isNull();
        assertThat(camera.urlExpiresAt()).isNull();
        verify(s3PresignedUrlService, never()).createGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 여러_CCTV의_최신_캡처를_세션_Query_한번으로_매핑한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv firstCctv = cctv(
                CCTV_ID,
                "CCTV_001",
                "CAM-1",
                1
        );
        Cctv secondCctv = cctv(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "CCTV_002",
                "CAM-2",
                2
        );
        LatestMonitoringCaptureItem firstCapture = LatestMonitoringCaptureItem.create(
                SESSION_ID, "CCTV_001", 1_000L, "monitoring/first.jpg"
        );
        LatestMonitoringCaptureItem secondCapture = LatestMonitoringCaptureItem.create(
                SESSION_ID, "CCTV_002", 2_000L, "monitoring/second.jpg"
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(firstCctv, secondCctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(firstCapture, secondCapture));
        given(s3PresignedUrlService.createGetUrl(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new PresignedGetUrl(
                        "https://example.com/frame.jpg",
                        Instant.parse("2026-08-27T01:00:00Z")
                ));

        MonitoringCameraListResponse response = service.getCameras(SESSION_ID, EMAIL);

        assertThat(response.cameras())
                .extracting(MonitoringCameraResponse::code, MonitoringCameraResponse::capturedAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("CCTV_001", 1_000L),
                        org.assertj.core.groups.Tuple.tuple("CCTV_002", 2_000L)
                );
        verify(latestMonitoringCaptureRepository)
                .findAllBySessionId(SESSION_ID.toString());
    }

    @Test
    void 일부_CCTV에만_캡처가_있어도_모든_활성_CCTV를_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv capturedCctv = cctv(
                CCTV_ID,
                "CCTV_001",
                "CAM-1",
                1
        );
        Cctv pendingCctv = cctv(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "CCTV_002",
                "CAM-2",
                2
        );
        LatestMonitoringCaptureItem capture = LatestMonitoringCaptureItem.create(
                SESSION_ID,
                "CCTV_001",
                1_000L,
                "monitoring/first.jpg"
        );
        Instant expiresAt = Instant.parse("2026-08-27T01:00:00Z");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(capturedCctv, pendingCctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(capture));
        given(s3PresignedUrlService.createGetUrl("monitoring/first.jpg"))
                .willReturn(new PresignedGetUrl("https://example.com/first.jpg", expiresAt));

        MonitoringCameraListResponse response = service.getCameras(SESSION_ID, EMAIL);

        assertThat(response.cameras()).hasSize(2);
        assertThat(response.cameras().get(0))
                .extracting(
                        MonitoringCameraResponse::code,
                        MonitoringCameraResponse::thumbnailUrl,
                        MonitoringCameraResponse::capturedAt,
                        MonitoringCameraResponse::urlExpiresAt
                )
                .containsExactly(
                        "CCTV_001",
                        "https://example.com/first.jpg",
                        1_000L,
                        expiresAt.toEpochMilli()
                );
        assertThat(response.cameras().get(1))
                .extracting(
                        MonitoringCameraResponse::code,
                        MonitoringCameraResponse::thumbnailUrl,
                        MonitoringCameraResponse::capturedAt,
                        MonitoringCameraResponse::urlExpiresAt
                )
                .containsExactly("CCTV_002", null, null, null);
        verify(s3PresignedUrlService).createGetUrl("monitoring/first.jpg");
    }

    @Test
    void 동일_CCTV는_최신_캡처_포인터의_프레임_한_장만_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(1);
        LatestMonitoringCaptureItem latestCapture = LatestMonitoringCaptureItem.create(
                SESSION_ID,
                "CCTV_001",
                2_000L,
                "monitoring/latest.jpg"
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(latestCapture));
        given(s3PresignedUrlService.createGetUrl("monitoring/latest.jpg"))
                .willReturn(new PresignedGetUrl(
                        "https://example.com/latest.jpg",
                        Instant.parse("2026-08-27T01:00:00Z")
                ));

        MonitoringCameraListResponse response = service.getCameras(SESSION_ID, EMAIL);

        assertThat(response.cameras()).singleElement().satisfies(camera -> {
            assertThat(camera.code()).isEqualTo("CCTV_001");
            assertThat(camera.thumbnailUrl()).isEqualTo("https://example.com/latest.jpg");
            assertThat(camera.capturedAt()).isEqualTo(2_000L);
        });
        verify(s3PresignedUrlService, never()).createGetUrl("monitoring/old.jpg");
    }

    @Test
    void 최신_관측의_이미지_키가_null이면_placeholder_응답을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(1);
        LatestMonitoringCaptureItem capture = LatestMonitoringCaptureItem.create(
                SESSION_ID,
                "CCTV_001",
                1_000L,
                null
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(capture));

        MonitoringCameraResponse camera = service.getCameras(SESSION_ID, EMAIL)
                .cameras().get(0);

        assertThat(camera.thumbnailUrl()).isNull();
        assertThat(camera.capturedAt()).isNull();
        assertThat(camera.urlExpiresAt()).isNull();
        verify(s3PresignedUrlService, never()).createGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 활성_CCTV가_없으면_빈_목록을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of());

        MonitoringCameraListResponse response = service.getCameras(SESSION_ID, EMAIL);

        assertThat(response.cameras()).isEmpty();
        verify(latestMonitoringCaptureRepository, never())
                .findAllBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 존재하지_않는_세션은_조회할_수_없다() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCameras(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.TRAINING_SESSION_NOT_FOUND
                );
        verify(cctvJpaRepository, never())
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        org.mockito.ArgumentMatchers.any());
        verify(latestMonitoringCaptureRepository, never())
                .findAllBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 다른_학교의_세션은_존재_여부를_노출하지_않고_조회할_수_없다() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCameras(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.TRAINING_SESSION_NOT_FOUND
                );
        verify(trainingSessionRepository)
                .findByIdAndScenario_Building_SchoolName(SESSION_ID, SCHOOL_NAME);
        verify(cctvJpaRepository, never())
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        org.mockito.ArgumentMatchers.any());
        verify(latestMonitoringCaptureRepository, never())
                .findAllBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void S3_조회_URL_발급에_실패하면_정의된_예외를_전파한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        given(cctv.getCode()).willReturn("CCTV_001");
        LatestMonitoringCaptureItem capture = LatestMonitoringCaptureItem.create(
                SESSION_ID,
                "CCTV_001",
                1_000L,
                "monitoring/frame.jpg"
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(latestMonitoringCaptureRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(capture));
        given(s3PresignedUrlService.createGetUrl("monitoring/frame.jpg"))
                .willThrow(new ApiException(S3ErrorCode.PRESIGNED_GET_URL_GENERATION_FAILED));

        assertThatThrownBy(() -> service.getCameras(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        S3ErrorCode.PRESIGNED_GET_URL_GENERATION_FAILED
                );
    }

    @Test
    void 실행_중이_아닌_세션은_조회할_수_없다() {
        stubSession(TrainingStatus.COMPLETED);

        assertThatThrownBy(() -> service.getCameras(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND
                );
        verify(cctvJpaRepository, never())
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 프레임_목록을_최신순으로_반환하고_다음_페이지가_있으면_커서를_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = frameCctv();
        ObservationItem newest = observation(3_000L, "monitoring/frame-3.jpg");
        ObservationItem middle = observation(2_000L, "monitoring/frame-2.jpg");
        ObservationItem oldest = observation(1_000L, "monitoring/frame-1.jpg");
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 3, null))
                .willReturn(List.of(newest, middle, oldest));
        given(s3PresignedUrlService.createGetUrl(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new PresignedGetUrl(
                        "https://example.com/frame.jpg",
                        Instant.parse("2026-08-27T01:00:00Z")
                ));

        MonitoringFrameListResponse response = service.getFrames(SESSION_ID, CCTV_ID, 2, null, EMAIL);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.cctvId()).isEqualTo(CCTV_ID);
        assertThat(response.frames())
                .extracting(MonitoringFrameResponse::capturedAt)
                .containsExactly(3_000L, 2_000L);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(FrameCursor.encode(2_000L));
    }

    @Test
    void 마지막_페이지면_nextCursor가_없다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        ObservationItem only = observation(1_000L, "monitoring/frame-1.jpg");
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, null))
                .willReturn(List.of(only));
        given(s3PresignedUrlService.createGetUrl("monitoring/frame-1.jpg"))
                .willReturn(new PresignedGetUrl(
                        "https://example.com/frame-1.jpg",
                        Instant.parse("2026-08-27T01:00:00Z")
                ));

        MonitoringFrameListResponse response = service.getFrames(SESSION_ID, CCTV_ID, 20, null, EMAIL);

        assertThat(response.frames()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void cursor를_전달하면_해당_시점_이전_프레임을_조회한다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, 1_000L))
                .willReturn(List.of());

        service.getFrames(SESSION_ID, CCTV_ID, 20, FrameCursor.encode(1_000L), EMAIL);

        verify(observationRepository)
                .findPageBySessionIdAndCctvCode(SESSION_ID.toString(), "CCTV_001", 21, 1_000L);
    }

    @Test
    void 이미지_키가_없는_프레임은_imageUrl이_null이다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        ObservationItem withoutImage = observation(1_000L, null);
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, null))
                .willReturn(List.of(withoutImage));

        MonitoringFrameResponse frame = service.getFrames(SESSION_ID, CCTV_ID, 20, null, EMAIL)
                .frames().get(0);

        assertThat(frame.imageUrl()).isNull();
        assertThat(frame.urlExpiresAt()).isNull();
        verify(s3PresignedUrlService, never()).createGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 세션이_속한_건물의_CCTV가_아니면_조회할_수_없다() {
        stubSession(TrainingStatus.RUNNING);
        given(cctvJpaRepository.findByIdAndCustomNode_Floor_Building_Id(CCTV_ID, BUILDING_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFrames(SESSION_ID, CCTV_ID, 20, null, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", CctvErrorCode.CCTV_NOT_FOUND);
        verify(observationRepository, never())
                .findPageBySessionIdAndCctvCode(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cursor_형식이_올바르지_않으면_400을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        given(cctvJpaRepository.findByIdAndCustomNode_Floor_Building_Id(CCTV_ID, BUILDING_ID))
                .willReturn(Optional.of(cctv));

        assertThatThrownBy(() -> service.getFrames(SESSION_ID, CCTV_ID, 20, "not-a-valid-cursor!!", EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    private Cctv frameCctv() {
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        given(cctv.getCode()).willReturn("CCTV_001");
        given(cctvJpaRepository.findByIdAndCustomNode_Floor_Building_Id(CCTV_ID, BUILDING_ID))
                .willReturn(Optional.of(cctv));
        return cctv;
    }

    private ObservationItem observation(long capturedAt, String monitoringImageKey) {
        return ObservationItem.create(
                UUID.randomUUID(),
                SESSION_ID,
                null,
                "CCTV_001",
                4.0,
                5,
                10,
                0.42,
                CongestionLevel.CROWDED,
                capturedAt - 500,
                capturedAt,
                capturedAt,
                monitoringImageKey,
                1L
        );
    }

    private void stubSession(TrainingStatus status) {
        TrainingSession session = org.mockito.Mockito.mock(TrainingSession.class);
        TrainingScenario scenario = org.mockito.Mockito.mock(TrainingScenario.class);
        Building building = org.mockito.Mockito.mock(Building.class);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.of(session));
        given(session.getStatus()).willReturn(status);
        if (status == TrainingStatus.RUNNING) {
            given(session.getScenario()).willReturn(scenario);
            given(scenario.getBuilding()).willReturn(building);
            given(building.getId()).willReturn(BUILDING_ID);
        }
    }

    private Cctv cctv(int floorNum) {
        return cctv(CCTV_ID, "CCTV_001", "CAM-1", floorNum);
    }

    private Cctv cctv(UUID cctvId, String code, String name, int floorNum) {
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        MapNode node = org.mockito.Mockito.mock(MapNode.class);
        Floor floor = org.mockito.Mockito.mock(Floor.class);
        Building building = org.mockito.Mockito.mock(Building.class);
        given(cctv.getId()).willReturn(cctvId);
        given(cctv.getCode()).willReturn(code);
        given(cctv.getName()).willReturn(name);
        given(cctv.getCustomNode()).willReturn(node);
        given(node.getFloor()).willReturn(floor);
        given(floor.getFloorNum()).willReturn(floorNum);
        given(floor.getBuilding()).willReturn(building);
        given(building.getName()).willReturn("A동");
        return cctv;
    }
}
