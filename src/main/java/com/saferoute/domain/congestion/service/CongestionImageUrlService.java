package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.response.CongestionImageUrlResponse;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ObservationItem;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.domain.telemetry.dynamo.repository.ObservationRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.domain.user.service.SchoolContextService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자 화면이 즉시 이벤트/관측값 이미지를 열람할 때 쓰는 Presigned GET URL 발급.
// S3 버킷은 Public이 아니라서 object key만으로는 브라우저에서 열 수 없다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionImageUrlService {

    private final CongestionEventRepository congestionEventRepository;
    private final ObservationRepository observationRepository;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final SchoolContextService schoolContextService;

    public CongestionImageUrlResponse getEventImageUrl(UUID eventId, String email) {
        CongestionEventItem item = congestionEventRepository.findByEventId(eventId.toString())
                .orElseThrow(() -> new ApiException(CongestionErrorCode.EVENT_NOT_FOUND));
        validateSessionSchool(item.getTrainingSessionId(), email);

        if (item.getImageUploadStatus() != ImageUploadStatus.COMPLETED || item.getEventImageKey() == null) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND);
        }

        return CongestionImageUrlResponse.from(s3PresignedUrlService.createGetUrl(item.getEventImageKey()));
    }

    public CongestionImageUrlResponse getObservationImageUrl(UUID eventId, String email) {
        ObservationItem item = observationRepository.findByEventId(eventId.toString())
                .orElseThrow(() -> new ApiException(CongestionErrorCode.EVENT_NOT_FOUND));
        validateSessionSchool(item.getTrainingSessionId(), email);

        if (item.getMonitoringImageKey() == null) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND);
        }

        return CongestionImageUrlResponse.from(s3PresignedUrlService.createGetUrl(item.getMonitoringImageKey()));
    }

    private void validateSessionSchool(String trainingSessionId, String email) {
        String schoolName = schoolContextService.getSchoolName(email);
        try {
            UUID sessionId = UUID.fromString(trainingSessionId);
            trainingSessionRepository.findByIdAndScenario_Building_SchoolName(sessionId, schoolName)
                    .orElseThrow(() -> new ApiException(CongestionErrorCode.EVENT_NOT_FOUND));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(CongestionErrorCode.EVENT_NOT_FOUND);
        }
    }
}
