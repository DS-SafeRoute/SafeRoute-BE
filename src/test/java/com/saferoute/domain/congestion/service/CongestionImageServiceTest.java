package com.saferoute.domain.congestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.saferoute.domain.building.entity.Building;
import com.saferoute.domain.congestion.dto.request.CongestionImageType;
import com.saferoute.domain.congestion.dto.request.CreatePresignedImageUrlRequest;
import com.saferoute.domain.congestion.dto.response.PresignedImageUrlResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.evacuation.graph.entity.MapNode;
import com.saferoute.domain.floor.entity.Floor;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import com.saferoute.infrastructure.s3.dto.PresignedPutUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CongestionImageServiceTest {

    private static final UUID CCTV_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REFERENCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID BUILDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final DevicePrincipal PRINCIPAL = new DevicePrincipal(CCTV_ID, "CCTV_001");

    @Mock
    private DeviceAuthorizationService deviceAuthorizationService;
    @Mock
    private TrainingSessionRepository trainingSessionRepository;
    @Mock
    private S3PresignedUrlService s3PresignedUrlService;
    @Mock
    private Cctv cctv;
    @Mock
    private MapNode customNode;
    @Mock
    private Floor floor;
    @Mock
    private Building building;
    @Mock
    private TrainingSession trainingSession;

    private CongestionImageService service;

    @BeforeEach
    void setUp() {
        service = new CongestionImageService(
                deviceAuthorizationService,
                trainingSessionRepository,
                s3PresignedUrlService
        );
    }

    @Test
    void createsMonitoringImageKeyFromCapturedAt() {
        CreatePresignedImageUrlRequest request = request(CongestionImageType.MONITORING);
        prepareAuthorizedRunningSession(request);
        given(s3PresignedUrlService.createPutUrl(
                "training/" + SESSION_ID + "/monitoring/CCTV_001/1786500005000.jpg",
                "image/jpeg"
        )).willReturn(presignedUrl());

        PresignedImageUrlResponse response = service.createUploadUrl(PRINCIPAL, request);

        assertThat(response.objectKey()).isEqualTo(
                "training/" + SESSION_ID + "/monitoring/CCTV_001/1786500005000.jpg");
        assertThat(response.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(response.expiresAt()).isEqualTo(1786500065000L);
    }

    @Test
    void createsEventImageKeyFromReferenceId() {
        CreatePresignedImageUrlRequest request = request(CongestionImageType.CONGESTION_EVENT);
        prepareAuthorizedRunningSession(request);
        String expectedKey = "training/" + SESSION_ID + "/events/CCTV_001/"
                + REFERENCE_ID + ".jpg";
        given(s3PresignedUrlService.createPutUrl(expectedKey, "image/jpeg"))
                .willReturn(presignedUrl());

        PresignedImageUrlResponse response = service.createUploadUrl(PRINCIPAL, request);

        assertThat(response.objectKey()).isEqualTo(expectedKey);
    }

    @Test
    void rejectsSessionThatIsNotRunningInCctvBuilding() {
        CreatePresignedImageUrlRequest request = request(CongestionImageType.MONITORING);
        given(deviceAuthorizationService.validateCctv(PRINCIPAL, "CCTV_001"))
                .willReturn(cctv);
        given(cctv.getCustomNode()).willReturn(customNode);
        given(customNode.getFloor()).willReturn(floor);
        given(floor.getBuilding()).willReturn(building);
        given(building.getId()).willReturn(BUILDING_ID);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                SESSION_ID, TrainingStatus.RUNNING, BUILDING_ID
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUploadUrl(PRINCIPAL, request))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND
                );
    }

    private void prepareAuthorizedRunningSession(CreatePresignedImageUrlRequest request) {
        given(deviceAuthorizationService.validateCctv(PRINCIPAL, request.cctvCode()))
                .willReturn(cctv);
        given(cctv.getCustomNode()).willReturn(customNode);
        given(customNode.getFloor()).willReturn(floor);
        given(floor.getBuilding()).willReturn(building);
        given(building.getId()).willReturn(BUILDING_ID);
        given(trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                SESSION_ID, TrainingStatus.RUNNING, BUILDING_ID
        )).willReturn(Optional.of(trainingSession));
    }

    private CreatePresignedImageUrlRequest request(CongestionImageType imageType) {
        return new CreatePresignedImageUrlRequest(
                UUID.randomUUID(),
                SESSION_ID,
                "CCTV_001",
                imageType,
                REFERENCE_ID,
                1786500005000L,
                "image/jpeg"
        );
    }

    private PresignedPutUrl presignedUrl() {
        return new PresignedPutUrl(
                "https://example.com/upload",
                Instant.ofEpochMilli(1786500065000L)
        );
    }
}
