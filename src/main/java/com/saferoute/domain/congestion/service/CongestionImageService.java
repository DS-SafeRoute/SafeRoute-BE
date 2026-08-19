package com.saferoute.domain.congestion.service;

import com.saferoute.domain.congestion.dto.request.CongestionImageType;
import com.saferoute.domain.congestion.dto.request.CreatePresignedImageUrlRequest;
import com.saferoute.domain.congestion.dto.response.PresignedImageUrlResponse;
import com.saferoute.domain.device.entity.Cctv;
import com.saferoute.domain.device.service.DeviceAuthorizationService;
import com.saferoute.domain.training.entity.TrainingStatus;
import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.api.error.TrainingErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.global.security.DevicePrincipal;
import com.saferoute.infrastructure.s3.dto.PresignedPutUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionImageService {

    private static final String IMAGE_CONTENT_TYPE = "image/jpeg";

    private final DeviceAuthorizationService deviceAuthorizationService;
    private final TrainingSessionRepository trainingSessionRepository;
    private final S3PresignedUrlService s3PresignedUrlService;

    public PresignedImageUrlResponse createUploadUrl(
            DevicePrincipal principal,
            CreatePresignedImageUrlRequest request
    ) {
        Cctv cctv = deviceAuthorizationService.validateCctv(principal, request.cctvCode());
        var buildingId = cctv.getCustomNode().getFloor().getBuilding().getId();

        trainingSessionRepository.findByIdAndStatusAndScenario_Building_Id(
                request.trainingSessionId(),
                TrainingStatus.RUNNING,
                buildingId
        ).orElseThrow(() ->
                new ApiException(TrainingErrorCode.RUNNING_TRAINING_SESSION_NOT_FOUND));

        String objectKey = createObjectKey(request, principal.cctvCode());
        PresignedPutUrl presignedPutUrl =
                s3PresignedUrlService.createPutUrl(objectKey, IMAGE_CONTENT_TYPE);
        return PresignedImageUrlResponse.from(objectKey, presignedPutUrl);
    }

    private String createObjectKey(
            CreatePresignedImageUrlRequest request,
            String authenticatedCctvCode
    ) {
        String sessionPrefix = "training/" + request.trainingSessionId();
        if (request.imageType() == CongestionImageType.MONITORING) {
            return sessionPrefix + "/monitoring/" + authenticatedCctvCode
                    + "/" + request.capturedAt() + ".jpg";
        }
        return sessionPrefix + "/events/" + authenticatedCctvCode
                + "/" + request.referenceId() + ".jpg";
    }
}
