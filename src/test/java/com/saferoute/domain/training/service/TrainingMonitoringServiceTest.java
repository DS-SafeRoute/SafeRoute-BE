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
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
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
        ObservationItem observation = org.mockito.Mockito.mock(ObservationItem.class);
        Instant expiresAt = Instant.parse("2026-08-27T01:00:00Z");
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(observationRepository.findLatestBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001"))
                .willReturn(Optional.of(observation));
        given(observation.getMonitoringImageKey())
                .willReturn("training/session/monitoring/CCTV_001/1787722095000.jpg");
        given(observation.getCapturedAt()).willReturn(1_787_722_095_000L);
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
        given(observationRepository.findLatestBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001"))
                .willReturn(Optional.empty());

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
        ObservationItem observation = org.mockito.Mockito.mock(ObservationItem.class);
        given(cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        BUILDING_ID))
                .willReturn(List.of(cctv));
        given(observationRepository.findLatestBySessionIdAndCctvCode(
                SESSION_ID.toString(), "CCTV_001"))
                .willReturn(Optional.of(observation));
        given(observation.getMonitoringImageKey()).willReturn(" ");

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
        verify(observationRepository, never())
                .findLatestBySessionIdAndCctvCode(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void 다른_학교이거나_없는_세션은_조회할_수_없다() {
        given(trainingSessionRepository.findByIdAndScenario_Building_SchoolName(
                SESSION_ID, SCHOOL_NAME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCameras(SESSION_ID, EMAIL))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.TRAINING_SESSION_NOT_FOUND
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
        Cctv cctv = org.mockito.Mockito.mock(Cctv.class);
        MapNode node = org.mockito.Mockito.mock(MapNode.class);
        Floor floor = org.mockito.Mockito.mock(Floor.class);
        Building building = org.mockito.Mockito.mock(Building.class);
        given(cctv.getId()).willReturn(CCTV_ID);
        given(cctv.getCode()).willReturn("CCTV_001");
        given(cctv.getName()).willReturn("CAM-1");
        given(cctv.getCustomNode()).willReturn(node);
        given(node.getFloor()).willReturn(floor);
        given(floor.getFloorNum()).willReturn(floorNum);
        given(floor.getBuilding()).willReturn(building);
        given(building.getName()).willReturn("A동");
        return cctv;
    }
}
