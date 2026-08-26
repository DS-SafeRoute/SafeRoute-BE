package com.saferoute.domain.training.service;

import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.domain.training.dto.MonitoringCameraListResponse;
import com.saferoute.domain.training.dto.MonitoringCameraResponse;
import com.saferoute.domain.training.entity.TrainingSession;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainingMonitoringService {

    private final TrainingSessionRepository trainingSessionRepository;
    private final CctvJpaRepository cctvJpaRepository;
    private final ObservationRepository observationRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final SchoolContextService schoolContextService;

    public MonitoringCameraListResponse getCameras(UUID sessionId, String email) {
        TrainingSession session = findRunningSessionForSchool(sessionId, email);
        UUID buildingId = session.getScenario().getBuilding().getId();
        List<Cctv> cctvs = cctvJpaRepository
                .findAllByEnabledTrueAndCustomNode_Floor_Building_IdOrderByCustomNode_Floor_FloorNumAscCodeAsc(
                        buildingId);

        List<MonitoringCameraResponse> cameras = cctvs.stream()
                .map(cctv -> toResponse(sessionId, cctv))
                .toList();
        return new MonitoringCameraListResponse(sessionId, cameras);
    }

    private TrainingSession findRunningSessionForSchool(UUID sessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        TrainingSession session = trainingSessionRepository
                .findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
                .orElseThrow(() -> new ApiException(TrainingErrorCode.TRAINING_SESSION_NOT_FOUND));
        if (session.getStatus() != TrainingStatus.RUNNING) {
            throw new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND);
        }
        return session;
    }

    private MonitoringCameraResponse toResponse(UUID sessionId, Cctv cctv) {
        return observationRepository
                .findLatestBySessionIdAndCctvCode(sessionId.toString(), cctv.getCode())
                .filter(this::hasMonitoringImage)
                .map(observation -> withCapture(cctv, observation))
                .orElseGet(() -> MonitoringCameraResponse.withoutCapture(cctv));
    }

    private boolean hasMonitoringImage(ObservationItem observation) {
        return observation.getMonitoringImageKey() != null
                && !observation.getMonitoringImageKey().isBlank();
    }

    private MonitoringCameraResponse withCapture(Cctv cctv, ObservationItem observation) {
        PresignedGetUrl presignedGetUrl =
                s3PresignedUrlService.createGetUrl(observation.getMonitoringImageKey());
        return MonitoringCameraResponse.withCapture(
                cctv,
                observation.getCapturedAt(),
                presignedGetUrl
        );
    }
}
