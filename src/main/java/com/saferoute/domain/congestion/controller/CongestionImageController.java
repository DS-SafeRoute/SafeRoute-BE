package com.saferoute.domain.congestion.controller;

import com.saferoute.domain.congestion.dto.request.CreatePresignedImageUrlRequest;
import com.saferoute.domain.congestion.dto.response.PresignedImageUrlResponse;
import com.saferoute.domain.congestion.service.CongestionImageService;
import com.saferoute.global.security.DevicePrincipal;
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

    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedImageUrlResponse> createPresignedUrl(
            @AuthenticationPrincipal DevicePrincipal principal,
            @Valid @RequestBody CreatePresignedImageUrlRequest request
    ) {
        return ResponseEntity.ok(congestionImageService.createUploadUrl(principal, request));
    }
}
