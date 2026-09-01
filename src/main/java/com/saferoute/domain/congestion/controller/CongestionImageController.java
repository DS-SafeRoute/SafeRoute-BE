package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.CreatePresignedImageUrlRequest;
import com.saferoute.domain.congestion.dto.response.PresignedImageUrlResponse;
import com.saferoute.domain.congestion.service.CongestionImageService;
import com.saferoute.global.security.DevicePrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "혼잡 이미지", description = "CCTV 혼잡 이미지 S3 직접 업로드 API")
@RestController
@RequestMapping("/api/v1/device/congestion-images")
@RequiredArgsConstructor
public class CongestionImageController {

    private final CongestionImageService congestionImageService;

    @Operation(
            summary = "혼잡 이미지 업로드 URL 발급",
            description = """
                    Pi가 모니터링 캡처(MONITORING) 또는 혼잡 이벤트(CONGESTION_EVENT) 이미지를 S3에
                    직접 업로드할 수 있도록 presigned PUT URL을 발급합니다. Pi는 이 URL로 이미지를
                    올린 뒤, 응답의 objectKey를 각각 관측값 신고(monitoringImageKey)나 이벤트 이미지
                    연결(PATCH /congestion-events/{eventId}/image)에 전달해야 합니다.

                    objectKey는 imageType에 따라 자동으로 결정됩니다. MONITORING이면
                    "training/{trainingSessionId}/monitoring/{cctvCode}/{capturedAt}.jpg",
                    그 외(CONGESTION_EVENT)면 "training/{trainingSessionId}/events/{cctvCode}/{referenceId}.jpg"
                    형식이며, 경로의 cctvCode는 요청 바디 값이 아니라 인증된 CCTV(Pi 토큰)의 코드로
                    고정됩니다. 이 요청은 해당 CCTV가 속한 건물에 RUNNING 상태 훈련 세션이 있을
                    때만 허용됩니다.

                    uploadUrl은 만료 시각(expiresAt, epoch millis)이 지나면 사용할 수 없으므로
                    발급 후 곧바로 업로드해야 하며, contentType은 image/jpeg만 허용됩니다.
                    """
    )
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedImageUrlResponse> createPresignedUrl(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody CreatePresignedImageUrlRequest request
    ) {
        return ResponseEntity.ok(congestionImageService.createUploadUrl(principal, request));
    }
}
