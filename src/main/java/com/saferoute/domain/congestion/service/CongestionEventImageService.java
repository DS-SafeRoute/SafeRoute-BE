package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.ConnectEventImageRequest;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.telemetry.dynamo.entity.CongestionEventItem;
import com.saferoute.domain.telemetry.dynamo.entity.EventProcessingStatus;
import com.saferoute.domain.telemetry.dynamo.entity.ImageUploadStatus;
import com.saferoute.domain.telemetry.dynamo.repository.CongestionEventRepository;
import com.saferoute.global.api.error.CongestionErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import com.saferoute.infrastructure.s3.service.S3Service;
import com.saferoute.infrastructure.websocket.service.TrainingEventPublisher;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionEventImageService {

    private static final String TRAINING_PREFIX = "training";
    private static final String EVENTS_DIRECTORY = "events";
    private static final String JPEG_SUFFIX = ".jpg";

    private final CongestionEventRepository congestionEventRepository;
    private final DeviceAuthorizationService deviceAuthorizationService;
    private final S3Service s3Service;
    private final TrainingEventPublisher trainingEventPublisher;

    public void connectImage(
            DevicePrincipal principal,
            UUID eventId,
            ConnectEventImageRequest request
    ) {
        CongestionEventItem item = findEvent(eventId);
        deviceAuthorizationService.validateCctv(principal, item.getCctvCode());
        validateEventProcessed(item);
        validateObjectKey(item, eventId, request.eventImageKey());

        if (isSameCompletedImage(item, request)) {
            publishImageUpdated(item);
            return;
        }
        validateImageState(item);

        if (!s3Service.objectExists(request.eventImageKey())) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_OBJECT_NOT_FOUND);
        }

        if (!congestionEventRepository.completeImageUpload(
                eventId.toString(), request.eventImageKey(), request.uploadedAt()
        )) {
            CongestionEventItem latest = findEvent(eventId);
            if (!isSameCompletedImage(latest, request)) {
                throw new ApiException(CongestionErrorCode.EVENT_IMAGE_STATE_CONFLICT);
            }
            publishImageUpdated(latest);
            return;
        }

        item.setEventImageKey(request.eventImageKey());
        item.setImageUploadedAt(request.uploadedAt());
        item.setImageUploadStatus(ImageUploadStatus.COMPLETED);
        publishImageUpdated(item);
    }

    private CongestionEventItem findEvent(UUID eventId) {
        return congestionEventRepository.findByEventId(eventId.toString())
                .orElseThrow(() -> new ApiException(CongestionErrorCode.EVENT_NOT_FOUND));
    }

    private void validateEventProcessed(CongestionEventItem item) {
        if (item.getEventStatus() != EventProcessingStatus.PROCESSED) {
            throw new ApiException(CongestionErrorCode.EVENT_NOT_PROCESSED);
        }
    }

    private void validateImageState(CongestionEventItem item) {
        if (item.getImageUploadStatus() != null
                && item.getImageUploadStatus() != ImageUploadStatus.PENDING
                && item.getImageUploadStatus() != ImageUploadStatus.FAILED) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_STATE_CONFLICT);
        }
    }

    private void validateObjectKey(CongestionEventItem item, UUID eventId, String objectKey) {
        String[] segments = objectKey.split("/", -1);
        if (segments.length != 5
                || !TRAINING_PREFIX.equals(segments[0])
                || !EVENTS_DIRECTORY.equals(segments[2])
                || !segments[4].endsWith(JPEG_SUFFIX)) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_KEY_INVALID);
        }

        UUID sessionId = parseCanonicalUuid(segments[1]);
        String imageEventId = segments[4].substring(0, segments[4].length() - JPEG_SUFFIX.length());
        UUID keyEventId = parseCanonicalUuid(imageEventId);
        boolean sameIdentity = Objects.equals(item.getTrainingSessionId(), sessionId.toString())
                && Objects.equals(item.getCctvCode(), segments[3])
                && eventId.equals(keyEventId);
        if (!sameIdentity) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_IDENTITY_MISMATCH);
        }
    }

    private UUID parseCanonicalUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equals(value)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(CongestionErrorCode.EVENT_IMAGE_KEY_INVALID, exception);
        }
    }

    private boolean isSameCompletedImage(
            CongestionEventItem item,
            ConnectEventImageRequest request
    ) {
        return item.getImageUploadStatus() == ImageUploadStatus.COMPLETED
                && Objects.equals(item.getEventImageKey(), request.eventImageKey())
                && Objects.equals(item.getImageUploadedAt(), request.uploadedAt());
    }

    private void publishImageUpdated(CongestionEventItem item) {
        trainingEventPublisher.publishCongestionEventImageUpdated(
                UUID.fromString(item.getTrainingSessionId()),
                item
        );
    }
}
