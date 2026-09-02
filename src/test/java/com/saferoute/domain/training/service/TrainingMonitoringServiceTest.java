package com.saferoute.domain.training.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.congestion.entity.CongestionConfig;
import com.saferoute.domain.congestion.service.CongestionConfigService;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.evacuation.recalculation.entity.RecalculationStatus;
import com.saferoute.domain.evacuation.recalculation.entity.RouteRecalculation;
import com.saferoute.domain.evacuation.recalculation.repository.RouteRecalculationRepository;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.congestion.entity.CongestionLevel;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventType;
import com.saferoute.domain.telemetry.dynamo.entity.CurrentCctvStateItem;
import com.saferoute.domain.telemetry.dynamo.entity.LatestMonitoringCaptureItem;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.CurrentCctvStateRepository;
import com.saferoute.domain.telemetry.dynamo.repository.LatestMonitoringCaptureRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.CurrentCctvStateListResponse;
import com.saferoute.domain.training.dto.CurrentCctvStateResponse;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.dto.MonitoringEventListResponse;
import com.saferoute.domain.training.dto.MonitoringEventResponse;
import com.saferoute.domain.training.dto.MonitoringEventType;
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
    private CongestionEventRepository congestionEventRepository;
    @Mock
    private CurrentCctvStateRepository currentCctvStateRepository;
    @Mock
    private RouteRecalculationRepository routeRecalculationRepository;
    @Mock
    private S3PresignedUrlService s3PresignedUrlService;
    @Mock
    private SchoolContextService schoolContextService;
    @Mock
    private CongestionConfigService congestionConfigService;

    private TrainingMonitoringService service;

    @BeforeEach
    void setUp() {
        service = new TrainingMonitoringService(
                trainingSessionRepository,
                cctvJpaRepository,
                latestMonitoringCaptureRepository,
                observationRepository,
                congestionEventRepository,
                currentCctvStateRepository,
                routeRecalculationRepository,
                s3PresignedUrlService,
                schoolContextService,
                congestionConfigService
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
    void 현재_상태가_있는_CCTV는_avg_peak를_분리해서_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(3);
        // 기본 설정 stateStaleAfterSec=15초 - 방금 관측된 상태이므로 stale이 아니어야 한다.
        long freshLastDetectedAt = Instant.now().toEpochMilli() - 1_000L;
        CurrentCctvStateItem state = CurrentCctvStateItem.create(
                SESSION_ID, "CCTV_001", 8.6, 12, 0.42, CongestionLevel.CROWDED, freshLastDetectedAt, 3L
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(currentCctvStateRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(state));
        given(congestionConfigService.getConfig()).willReturn(CongestionConfig.createDefault());

        CurrentCctvStateListResponse response = service.getCurrentStates(SESSION_ID, EMAIL);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.states()).singleElement().satisfies(cctvState -> {
            assertThat(cctvState.cctvCode()).isEqualTo("CCTV_001");
            assertThat(cctvState.avgHeadcount()).isEqualTo(8.6);
            assertThat(cctvState.peakHeadcount()).isEqualTo(12);
            assertThat(cctvState.density()).isEqualTo(0.42);
            assertThat(cctvState.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
            assertThat(cctvState.lastDetectedAt()).isEqualTo(freshLastDetectedAt);
            assertThat(cctvState.stale()).isFalse();
            assertThat(cctvState.configVersion()).isEqualTo(3L);
        });
    }

    @Test
    void 상태가_없는_CCTV도_목록에서_누락하지_않고_stale로_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(3);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(currentCctvStateRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of());
        given(congestionConfigService.getConfig()).willReturn(CongestionConfig.createDefault());

        CurrentCctvStateResponse state = service.getCurrentStates(SESSION_ID, EMAIL).states().get(0);

        assertThat(state.avgHeadcount()).isNull();
        assertThat(state.peakHeadcount()).isNull();
        assertThat(state.density()).isNull();
        assertThat(state.congestionLevel()).isNull();
        assertThat(state.lastDetectedAt()).isNull();
        assertThat(state.stale()).isTrue();
    }

    @Test
    void stateStaleAfterSec를_초과한_상태는_stale로_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = cctv(3);
        // 기본 설정 stateStaleAfterSec=15초. lastDetectedAt을 현재로부터 20초 전으로 만들어 stale 유도.
        long staleLastDetectedAt = Instant.now().toEpochMilli() - 20_000L;
        CurrentCctvStateItem state = CurrentCctvStateItem.create(
                SESSION_ID, "CCTV_001", 8.6, 12, 0.42, CongestionLevel.CROWDED, staleLastDetectedAt, 3L
        );
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(currentCctvStateRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(state));
        given(congestionConfigService.getConfig()).willReturn(CongestionConfig.createDefault());

        CurrentCctvStateResponse response = service.getCurrentStates(SESSION_ID, EMAIL).states().get(0);

        assertThat(response.stale()).isTrue();
        assertThat(response.congestionLevel()).isEqualTo(CongestionLevel.CROWDED);
    }

    @Test
    void 활성_CCTV가_없으면_현재_상태_조회도_빈_목록을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of());

        CurrentCctvStateListResponse response = service.getCurrentStates(SESSION_ID, EMAIL);

        assertThat(response.states()).isEmpty();
        verify(currentCctvStateRepository, never()).findAllBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 현재_상태_조회는_실행_중이_아닌_세션은_거부한다() {
        stubSession(TrainingStatus.COMPLETED);

        assertThatThrownBy(() -> service.getCurrentStates(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND);
        verify(cctvJpaRepository, never())
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 프레임_목록을_최신순으로_반환하고_다음_페이지가_있으면_커서를_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        Cctv cctv = frameCctv();
        UUID newestEventId = UUID.randomUUID();
        UUID middleEventId = UUID.randomUUID();
        UUID oldestEventId = UUID.randomUUID();
        ObservationItem newest = observation(newestEventId, 3_000L, "monitoring/frame-3.jpg");
        ObservationItem middle = observation(middleEventId, 2_000L, "monitoring/frame-2.jpg");
        ObservationItem oldest = observation(oldestEventId, 1_000L, "monitoring/frame-1.jpg");
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 3, null, null))
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
        assertThat(response.nextCursor())
                .isEqualTo(FrameCursor.encode(2_000L, middleEventId.toString()));
    }

    @Test
    void 마지막_페이지면_nextCursor가_없다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        ObservationItem only = observation(UUID.randomUUID(), 1_000L, "monitoring/frame-1.jpg");
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, null, null))
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
        UUID cursorEventId = UUID.randomUUID();
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, 1_000L, cursorEventId.toString()))
                .willReturn(List.of());

        service.getFrames(
                SESSION_ID, CCTV_ID, 20, FrameCursor.encode(1_000L, cursorEventId.toString()), EMAIL);

        verify(observationRepository).findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, 1_000L, cursorEventId.toString());
    }

    @Test
    void 캡처_시각이_같은_프레임이_페이지_경계에_있어도_누락되지_않는다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();
        ObservationItem first = observation(firstEventId, 1_000L, "monitoring/first.jpg");
        ObservationItem second = observation(secondEventId, 1_000L, "monitoring/second.jpg");
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 2, null, null))
                .willReturn(List.of(first, second));
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 2, 1_000L, firstEventId.toString()))
                .willReturn(List.of(second));
        given(s3PresignedUrlService.createGetUrl(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new PresignedGetUrl(
                        "https://example.com/frame.jpg",
                        Instant.parse("2026-08-27T01:00:00Z")
                ));

        MonitoringFrameListResponse firstPage = service.getFrames(SESSION_ID, CCTV_ID, 1, null, EMAIL);
        MonitoringFrameListResponse secondPage = service.getFrames(
                SESSION_ID, CCTV_ID, 1, firstPage.nextCursor(), EMAIL);

        assertThat(firstPage.frames()).extracting(MonitoringFrameResponse::frameId)
                .containsExactly(firstEventId.toString());
        assertThat(secondPage.frames()).extracting(MonitoringFrameResponse::frameId)
                .containsExactly(secondEventId.toString());
    }

    @Test
    void 이미지_키가_없는_프레임은_imageUrl이_null이다() {
        stubSession(TrainingStatus.RUNNING);
        frameCctv();
        ObservationItem withoutImage = observation(UUID.randomUUID(), 1_000L, null);
        given(observationRepository.findPageBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001", 21, null, null))
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
                        org.mockito.ArgumentMatchers.any(),
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

    @Test
    void 혼잡_이벤트와_재탐색_이벤트를_발생_시각순으로_합쳐_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        CongestionEventItem congestionEvent = congestionEventItem(
                "CCTV_001", CongestionEventType.CONGESTION_STARTED, 1_000L, CongestionLevel.CAUTION);
        given(congestionEventRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(congestionEvent));
        RouteRecalculation recalculation = recalculation(
                "CCTV_001", CongestionLevel.CROWDED, Instant.ofEpochMilli(2_000L), null, null);
        given(routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(SESSION_ID))
                .willReturn(List.of(recalculation));

        MonitoringEventListResponse response = service.getEvents(SESSION_ID, null, EMAIL);

        assertThat(response.sessionId()).isEqualTo(SESSION_ID);
        assertThat(response.events())
                .extracting(MonitoringEventResponse::type, MonitoringEventResponse::occurredAt)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MonitoringEventType.CONGESTION_STARTED, 1_000L),
                        org.assertj.core.groups.Tuple.tuple(MonitoringEventType.ROUTE_RECALCULATION_REQUESTED, 2_000L)
                );
    }

    @Test
    void 해소된_재탐색은_요청과_해소_두_이벤트로_나뉜다() {
        stubSession(TrainingStatus.RUNNING);
        given(congestionEventRepository.findAllBySessionId(SESSION_ID.toString())).willReturn(List.of());
        RouteRecalculation recalculation = recalculation(
                "CCTV_001", CongestionLevel.CROWDED,
                Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(3_000L), RecalculationStatus.APPROVED);
        given(routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(SESSION_ID))
                .willReturn(List.of(recalculation));

        MonitoringEventListResponse response = service.getEvents(SESSION_ID, null, EMAIL);

        assertThat(response.events())
                .extracting(MonitoringEventResponse::type)
                .containsExactly(
                        MonitoringEventType.ROUTE_RECALCULATION_REQUESTED,
                        MonitoringEventType.EVACUATION_ROUTE_UPDATED
                );
    }

    @Test
    void cctvCode로_필터링한다() {
        stubSession(TrainingStatus.RUNNING);
        CongestionEventItem matching = congestionEventItem(
                "CCTV_001", CongestionEventType.CONGESTION_STARTED, 1_000L, CongestionLevel.CAUTION);
        CongestionEventItem other = congestionEventItem(
                "CCTV_002", CongestionEventType.CONGESTION_STARTED, 1_500L, CongestionLevel.CAUTION);
        given(congestionEventRepository.findAllBySessionId(SESSION_ID.toString()))
                .willReturn(List.of(matching, other));
        given(routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(SESSION_ID))
                .willReturn(List.of());

        MonitoringEventListResponse response = service.getEvents(SESSION_ID, "CCTV_001", EMAIL);

        assertThat(response.events()).singleElement()
                .extracting(MonitoringEventResponse::cctvCode)
                .isEqualTo("CCTV_001");
    }

    @Test
    void 이벤트가_없는_세션은_빈_타임라인을_반환한다() {
        stubSession(TrainingStatus.RUNNING);
        given(congestionEventRepository.findAllBySessionId(SESSION_ID.toString())).willReturn(List.of());
        given(routeRecalculationRepository.findAllByTrainingSession_IdOrderByRequestedAtDesc(SESSION_ID))
                .willReturn(List.of());

        MonitoringEventListResponse response = service.getEvents(SESSION_ID, null, EMAIL);

        assertThat(response.events()).isEmpty();
    }

    @Test
    void 이벤트_타임라인_조회는_실행_중이_아닌_세션은_거부한다() {
        stubSession(TrainingStatus.COMPLETED);

        assertThatThrownBy(() -> service.getEvents(SESSION_ID, null, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND);
        verify(congestionEventRepository, never()).findAllBySessionId(org.mockito.ArgumentMatchers.anyString());
    }

    private CongestionEventItem congestionEventItem(
            String cctvCode, CongestionEventType type, long detectedAt, CongestionLevel level) {
        return CongestionEventItem.received(
                UUID.randomUUID(), SESSION_ID, cctvCode, type, detectedAt,
                5, 0.3, CongestionLevel.NORMAL, 0.5, level, 1L, null);
    }

    private RouteRecalculation recalculation(
            String cctvCode, CongestionLevel level, Instant requestedAt, Instant resolvedAt,
            RecalculationStatus status) {
        RouteRecalculation recalculation = org.mockito.Mockito.mock(RouteRecalculation.class);
        given(recalculation.getId()).willReturn(UUID.randomUUID());
        given(recalculation.getCctvCode()).willReturn(cctvCode);
        given(recalculation.getCongestionLevel()).willReturn(level);
        given(recalculation.getRequestedAt()).willReturn(requestedAt);
        given(recalculation.getResolvedAt()).willReturn(resolvedAt);
        if (resolvedAt != null) {
            given(recalculation.getStatus()).willReturn(status);
        }
        return recalculation;
    }

    private Cctv frameCctv() {
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        given(cctv.getCode()).willReturn("CCTV_001");
        given(cctvJpaRepository.findByIdAndCustomNode_Floor_Building_Id(CCTV_ID, BUILDING_ID))
                .willReturn(Optional.of(cctv));
        return cctv;
    }

    private ObservationItem observation(UUID eventId, long capturedAt, String monitoringImageKey) {
        return ObservationItem.create(
                eventId,
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

    private TrainingSession stubSession(TrainingStatus status) {
        TrainingSession session = org.mockito.Mockito.mock(TrainingSession.class);
        TrainingScenario scenario = org.mockito.Mockito.mock(TrainingScenario.class);
        Building building = org.mockito.Mockito.mock(Building.class);
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.of(session));
        given(session.getStatus()).willReturn(status);
        if (status == TrainingStatus.RUNNING) {
            // getEvents()는 건물 정보를 쓰지 않으므로, 그 테스트들에서는 아래 스텁이 사용되지 않는다 - lenient 처리.
            org.mockito.Mockito.lenient().when(session.getScenario()).thenReturn(scenario);
            org.mockito.Mockito.lenient().when(scenario.getBuilding()).thenReturn(building);
            org.mockito.Mockito.lenient().when(building.getId()).thenReturn(BUILDING_ID);
        }
        return session;
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
